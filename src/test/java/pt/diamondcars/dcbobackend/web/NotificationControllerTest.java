package pt.diamondcars.dcbobackend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import pt.diamondcars.dcbobackend.domain.notification.Notification;
import pt.diamondcars.dcbobackend.domain.notification.NotificationRepository;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;

/**
 * End-to-end tests of {@link NotificationController}, {@link
 * pt.diamondcars.dcbobackend.service.NotificationService} and {@link ApiExceptionHandler} through
 * the real servlet filter chain (TASK-010), using {@link MockMvc} against a real PostgreSQL
 * container ({@link AbstractPostgresIntegrationTest}), mirroring the idiom {@code
 * ClientControllerTest} (TASK-009) established.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NotificationControllerTest extends AbstractPostgresIntegrationTest {

	private static final String USER_ROLE = "ROLE_USER";

	@Autowired private MockMvc mockMvc;
	@Autowired private NotificationRepository notificationRepository;

	/**
	 * Clears every row this class writes before each test, for the same reason {@code
	 * CarControllerTest#cleanDatabase()} does (shared, JVM-wide singleton container, no per-test
	 * rollback).
	 */
	@BeforeEach
	void cleanDatabase() {
		notificationRepository.deleteAll();
	}

	/**
	 * Acceptance criterion 6: {@code PATCH /api/notifications/{id}/read} turns {@code read = true},
	 * and calling it a second time on the same notification is a no-op, not an error (idempotent).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void markingANotificationAsReadIsIdempotent() throws Exception {
		Notification notification =
				notificationRepository.saveAndFlush(Notification.builder().tipo("follow_up").mensagem("Msg").build());

		mockMvc
				.perform(patch("/api/notifications/{id}/read", notification.getId())
						.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(true));

		mockMvc
				.perform(patch("/api/notifications/{id}/read", notification.getId())
						.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(true));

		assertThat(notificationRepository.findById(notification.getId()).orElseThrow().isRead()).isTrue();
	}

	/**
	 * {@code PATCH /api/notifications/{id}/read} for an id that does not exist responds 404.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void markingAnUnknownNotificationAsReadReturns404() throws Exception {
		mockMvc
				.perform(patch("/api/notifications/{id}/read", UUID.randomUUID())
						.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNotFound());
	}

	/**
	 * {@code GET /api/notifications?read=false} returns only unread notifications.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listFiltersNotificationsByRead() throws Exception {
		notificationRepository.saveAndFlush(
				Notification.builder().tipo("follow_up").mensagem("Unread").build());
		notificationRepository.saveAndFlush(
				Notification.builder().tipo("follow_up").mensagem("Read").read(true).build());

		mockMvc
				.perform(get("/api/notifications?read=false")
						.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].mensagem").value("Unread"));
	}

	/**
	 * {@code POST /api/notifications/read-all} marks every unread notification as read, in one
	 * request.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void readAllMarksEveryUnreadNotificationAsRead() throws Exception {
		Notification first =
				notificationRepository.saveAndFlush(Notification.builder().tipo("follow_up").mensagem("A").build());
		Notification second =
				notificationRepository.saveAndFlush(Notification.builder().tipo("follow_up").mensagem("B").build());

		mockMvc
				.perform(post("/api/notifications/read-all")
						.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNoContent());

		assertThat(notificationRepository.findById(first.getId()).orElseThrow().isRead()).isTrue();
		assertThat(notificationRepository.findById(second.getId()).orElseThrow().isRead()).isTrue();
	}

	/**
	 * {@code DELETE /api/notifications/{id}} removes the notification.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletingANotificationRemovesIt() throws Exception {
		Notification notification =
				notificationRepository.saveAndFlush(Notification.builder().tipo("follow_up").mensagem("A remover").build());

		mockMvc
				.perform(delete("/api/notifications/{id}", notification.getId())
						.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNoContent());

		assertThat(notificationRepository.existsById(notification.getId())).isFalse();
	}

	/**
	 * {@code GET /api/notifications} without an {@code Authorization} header responds 401 — the
	 * global rule from {@code SecurityConfig} (TASK-007), re-verified against this business
	 * endpoint.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listingNotificationsWithoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
	}
}

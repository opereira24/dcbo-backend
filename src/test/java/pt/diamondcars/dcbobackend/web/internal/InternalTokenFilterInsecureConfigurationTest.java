package pt.diamondcars.dcbobackend.web.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pt.diamondcars.dcbobackend.domain.lead.LeadRepository;
import pt.diamondcars.dcbobackend.domain.notification.NotificationRepository;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;
import pt.diamondcars.dcbobackend.web.dto.InternalLeadRequest;

/**
 * Regression test for IMPORTANTE 1 ({@code backlog/reviews/TASK-010-r1.md}): when {@code
 * catalog.sync.internal-token} is left at its {@code application.yml} placeholder default (e.g.
 * because {@code CATALOG_SYNC_TOKEN} was never set, a plausible Render misconfiguration), {@link
 * pt.diamondcars.dcbobackend.config.InternalTokenFilter} must reject every {@code /internal/**}
 * request — including one presenting {@code X-Internal-Token: placeholder} itself, since that
 * literal is visible in the repository and therefore not a secret at all.
 *
 * <p>A separate {@link SpringBootTest} context from {@link InternalLeadControllerTest} (which
 * fixes a real, sufficiently long token) is required here specifically to boot the application
 * with the insecure default.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "catalog.sync.internal-token=placeholder")
@AutoConfigureMockMvc
class InternalTokenFilterInsecureConfigurationTest extends AbstractPostgresIntegrationTest {

	private static final String TOKEN_HEADER = "X-Internal-Token";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private LeadRepository leadRepository;
	@Autowired private NotificationRepository notificationRepository;

	/**
	 * Clears every row this class writes before each test, for the same reason {@code
	 * InternalLeadControllerTest#cleanDatabase()} does (shared, JVM-wide singleton container, no
	 * per-test rollback).
	 */
	@BeforeEach
	void cleanDatabase() {
		notificationRepository.deleteAll();
		leadRepository.deleteAll();
	}

	/**
	 * With {@code catalog.sync.internal-token} left at the placeholder default, {@code X-Internal-
	 * Token: placeholder} — the exact literal a misconfigured deployment would silently accept
	 * before this fix — responds 401, and no lead is created.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsThePlaceholderTokenEvenWhenItMatchesTheConfiguredDefault() throws Exception {
		InternalLeadRequest request =
				new InternalLeadRequest(
						"Maria Visitante", "maria@example.com", "912345678", "Quero saber mais", null, "Audi", "A4",
						"website");

		mockMvc
				.perform(
						post("/internal/leads")
								.header(TOKEN_HEADER, "placeholder")
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized());

		assertThat(leadRepository.count()).isZero();
		assertThat(notificationRepository.count()).isZero();
	}
}

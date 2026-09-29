package pt.diamondcars.dcbobackend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import pt.diamondcars.dcbobackend.domain.user.AppUser;
import pt.diamondcars.dcbobackend.domain.user.AppUserRepository;
import pt.diamondcars.dcbobackend.domain.user.AppUserRole;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;
import pt.diamondcars.dcbobackend.web.dto.AppUserActiveRequest;
import pt.diamondcars.dcbobackend.web.dto.AppUserRequest;
import pt.diamondcars.dcbobackend.web.dto.AppUserUpdateRequest;

/**
 * End-to-end tests of {@link AppUserController}, {@link
 * pt.diamondcars.dcbobackend.service.AppUserService}, {@link
 * pt.diamondcars.dcbobackend.config.ActiveUserInterceptor}, and {@link ApiExceptionHandler}
 * through the real servlet filter chain (TASK-012), using {@link MockMvc} against a real
 * PostgreSQL container ({@link AbstractPostgresIntegrationTest}), mirroring the idiom {@code
 * PartnerControllerTest} (TASK-009) established.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AppUserControllerTest extends AbstractPostgresIntegrationTest {

	private static final String USER_ROLE = "ROLE_USER";
	private static final String ADMIN_ROLE = "ROLE_ADMIN";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private AppUserRepository appUserRepository;

	/**
	 * Clears every row this class writes before each test, for the same reason {@code
	 * CarControllerTest#cleanDatabase()} does (shared, JVM-wide singleton container, no per-test
	 * rollback).
	 */
	@BeforeEach
	void cleanDatabase() {
		appUserRepository.deleteAll();
	}

	private static AppUserRequest validRequest(String authSubject) {
		return new AppUserRequest(authSubject, "novo@diamondcars.pt", "Novo Utilizador", "user");
	}

	private AppUser anAppUser(String authSubject, AppUserRole role, boolean active) {
		return appUserRepository.saveAndFlush(
				AppUser.builder()
						.authSubject(authSubject)
						.email(authSubject + "@diamondcars.pt")
						.name("Utilizador " + authSubject)
						.role(role)
						.active(active)
						.build());
	}

	/**
	 * Acceptance criterion 1: an admin JWT gets 200 with the paginated list of users.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void adminListsUsersReturns200WithThePaginatedList() throws Exception {
		anAppUser("auth0|list-1", AppUserRole.USER, true);
		anAppUser("auth0|list-2", AppUserRole.ADMIN, true);

		mockMvc
				.perform(get("/api/users").with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2));
	}

	/**
	 * Acceptance criterion 2: a JWT without the admin role gets 403 on {@code GET /api/users}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void nonAdminListingUsersReturns403() throws Exception {
		mockMvc
				.perform(get("/api/users").with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isForbidden());
	}

	/**
	 * Creates a profile and reads it back by id (201 + {@code Location}, then 200).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void createsAUserAndReadsItBackById() throws Exception {
		String location =
				mockMvc
						.perform(
								post("/api/users")
										.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(validRequest("auth0|new-user"))))
						.andExpect(status().isCreated())
						.andExpect(header().string("Location", notNullValue()))
						.andExpect(jsonPath("$.authSubject").value("auth0|new-user"))
						.andExpect(jsonPath("$.role").value("user"))
						.andExpect(jsonPath("$.active").value(true))
						.andReturn()
						.getResponse()
						.getHeader("Location");

		mockMvc
				.perform(get(location).with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("novo@diamondcars.pt"));
	}

	/**
	 * Acceptance criterion 3: creating a profile for an {@code authSubject} that already has one
	 * responds 409.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void creatingAUserWithADuplicateAuthSubjectReturns409() throws Exception {
		anAppUser("auth0|duplicate", AppUserRole.USER, true);

		mockMvc
				.perform(
						post("/api/users")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(validRequest("auth0|duplicate"))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	/**
	 * Requirement 2: a {@code role} outside the enum is rejected with 400, naming the field.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsAUserWithAnInvalidRole() throws Exception {
		AppUserRequest invalid = new AppUserRequest("auth0|bad-role", "bad@diamondcars.pt", "Mau Role", "superadmin");

		mockMvc
				.perform(
						post("/api/users")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(invalid)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("role")));
	}

	/**
	 * A {@code PUT /api/users/{id}} replaces name and role.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void updatingAUserReplacesItsNameAndRole() throws Exception {
		AppUser user = anAppUser("auth0|to-update", AppUserRole.USER, true);

		mockMvc
				.perform(
						put("/api/users/{id}", user.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(
										objectMapper.writeValueAsString(
												new AppUserUpdateRequest("Nome Atualizado", "admin"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Nome Atualizado"))
				.andExpect(jsonPath("$.role").value("admin"));
	}

	/**
	 * Acceptance criterion 4: a user deactivated via {@code PATCH /api/users/{id}/active} can no
	 * longer make authenticated requests — {@code GET /api/cars}, chosen because it belongs to a
	 * different controller entirely, responds 403 for that same {@code sub} afterwards.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deactivatingAUserBlocksFurtherAuthenticatedRequests() throws Exception {
		AppUser user = anAppUser("auth0|to-deactivate", AppUserRole.USER, true);

		mockMvc
				.perform(
						patch("/api/users/{id}/active", user.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(new AppUserActiveRequest(false))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc
				.perform(
						get("/api/cars")
								.with(
										jwt().jwt(jwt -> jwt.subject("auth0|to-deactivate"))
												.authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isForbidden());

		assertThat(appUserRepository.findById(user.getId()).orElseThrow().isActive()).isFalse();
	}

	/**
	 * Reactivating a previously deactivated user restores access.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void reactivatingAUserRestoresAccess() throws Exception {
		anAppUser("auth0|to-reactivate", AppUserRole.USER, false);
		AppUser user = appUserRepository.findByAuthSubject("auth0|to-reactivate").orElseThrow();

		mockMvc
				.perform(
						patch("/api/users/{id}/active", user.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(new AppUserActiveRequest(true))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));

		mockMvc
				.perform(
						get("/api/cars")
								.with(
										jwt().jwt(jwt -> jwt.subject("auth0|to-reactivate"))
												.authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk());
	}

	/**
	 * Acceptance criterion 5: {@code GET /api/me} returns the caller's own profile.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void getMeReturnsTheCallersOwnProfile() throws Exception {
		anAppUser("auth0|self", AppUserRole.ADMIN, true);

		mockMvc
				.perform(
						get("/api/me")
								.with(
										jwt().jwt(jwt -> jwt.subject("auth0|self"))
												.authorities(new SimpleGrantedAuthority(ADMIN_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authSubject").value("auth0|self"))
				.andExpect(jsonPath("$.role").value("admin"));
	}

	/**
	 * Acceptance criterion 5 (counterpart): {@code GET /api/me} for a {@code sub} with no local
	 * profile responds 404.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void getMeWithoutALocalProfileReturns404() throws Exception {
		mockMvc
				.perform(
						get("/api/me")
								.with(
										jwt().jwt(jwt -> jwt.subject("auth0|no-profile"))
												.authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNotFound());
	}

	/**
	 * Acceptance criterion 6: an admin calling {@code DELETE /api/users/{seu-proprio-id}} gets 409,
	 * and the profile still exists afterwards.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void adminCannotDeleteTheirOwnProfile() throws Exception {
		AppUser self = anAppUser("auth0|self-delete", AppUserRole.ADMIN, true);

		mockMvc
				.perform(
						delete("/api/users/{id}", self.getId())
								.with(
										jwt().jwt(jwt -> jwt.subject("auth0|self-delete"))
												.authorities(new SimpleGrantedAuthority(ADMIN_ROLE))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));

		assertThat(appUserRepository.existsById(self.getId())).isTrue();
	}

	/**
	 * Counterpart of {@link #adminCannotDeleteTheirOwnProfile()}: deleting a different profile
	 * succeeds (204).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void adminCanDeleteAnotherUsersProfile() throws Exception {
		AppUser other = anAppUser("auth0|other", AppUserRole.USER, true);

		mockMvc
				.perform(
						delete("/api/users/{id}", other.getId())
								.with(
										jwt().jwt(jwt -> jwt.subject("auth0|admin-caller"))
												.authorities(new SimpleGrantedAuthority(ADMIN_ROLE))))
				.andExpect(status().isNoContent());

		assertThat(appUserRepository.existsById(other.getId())).isFalse();
	}

	/**
	 * The global rule from {@code SecurityConfig} (TASK-007), re-verified against this business
	 * endpoint: no {@code Authorization} header responds 401.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listingUsersWithoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
	}
}

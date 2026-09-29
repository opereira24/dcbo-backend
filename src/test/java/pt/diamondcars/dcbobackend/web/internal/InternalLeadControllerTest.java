package pt.diamondcars.dcbobackend.web.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import pt.diamondcars.dcbobackend.domain.lead.LeadRepository;
import pt.diamondcars.dcbobackend.domain.notification.NotificationRepository;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;
import pt.diamondcars.dcbobackend.web.dto.InternalLeadRequest;

/**
 * End-to-end tests of {@link InternalLeadController}, {@link
 * pt.diamondcars.dcbobackend.service.LeadService#createFromWebsite}, {@link
 * pt.diamondcars.dcbobackend.config.InternalTokenFilter} and {@code SecurityConfig}'s {@code
 * /internal/**} rule (TASK-010, requirement 2), through the real servlet filter chain, using
 * {@link MockMvc} against a real PostgreSQL container ({@link AbstractPostgresIntegrationTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class InternalLeadControllerTest extends AbstractPostgresIntegrationTest {

	private static final String TOKEN_HEADER = "X-Internal-Token";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private LeadRepository leadRepository;
	@Autowired private NotificationRepository notificationRepository;

	@Value("${catalog.sync.internal-token}")
	private String validToken;

	/**
	 * Clears every row this class writes before each test, for the same reason {@code
	 * CarControllerTest#cleanDatabase()} does (shared, JVM-wide singleton container, no per-test
	 * rollback).
	 */
	@BeforeEach
	void cleanDatabase() {
		notificationRepository.deleteAll();
		leadRepository.deleteAll();
	}

	private static InternalLeadRequest validRequest() {
		return new InternalLeadRequest(
				"Maria Visitante", "maria@example.com", "912345678", "Quero saber mais", null, "Audi", "A4",
				"website");
	}

	/**
	 * Acceptance criterion 3: {@code POST /internal/leads} without the token header responds 401,
	 * and no lead is created.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsARequestWithoutTheTokenHeader() throws Exception {
		mockMvc
				.perform(
						post("/internal/leads")
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isUnauthorized());

		assertThat(leadRepository.count()).isZero();
	}

	/**
	 * {@code POST /internal/leads} with a wrong token responds 401, and no lead is created.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsARequestWithTheWrongToken() throws Exception {
		mockMvc
				.perform(
						post("/internal/leads")
								.header(TOKEN_HEADER, "token-errado")
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isUnauthorized());

		assertThat(leadRepository.count()).isZero();
	}

	/**
	 * Acceptance criterion 5: {@code POST /internal/leads} with a valid Auth0 JWT but without the
	 * shared token still responds 401 — this path never accepts JWT authentication.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsARequestAuthenticatedOnlyWithAJwt() throws Exception {
		mockMvc
				.perform(
						post("/internal/leads")
								.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isUnauthorized());

		assertThat(leadRepository.count()).isZero();
	}

	/**
	 * Acceptance criterion 4: {@code POST /internal/leads} with the correct token and a valid
	 * payload responds 201, the lead is created with {@code status = "ativo"}, and exactly one new
	 * notification exists, associated with that lead (requirement 3).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void acceptsAValidRequestAndCreatesTheAssociatedNotification() throws Exception {
		String location =
				mockMvc
						.perform(
								post("/internal/leads")
										.header(TOKEN_HEADER, validToken)
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(validRequest())))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.status").value("ativo"))
						.andExpect(jsonPath("$.notas").value("Quero saber mais"))
						.andReturn()
						.getResponse()
						.getHeader("Location");

		assertThat(location).isNotNull();
		assertThat(leadRepository.count()).isEqualTo(1);
		assertThat(notificationRepository.findAll()).hasSize(1);
		assertThat(notificationRepository.findAll().get(0).getLead().getId())
				.isEqualTo(leadRepository.findAll().get(0).getId());
	}

	/**
	 * Acceptance criterion 7 (second half): {@code POST /internal/leads} with {@code origem =
	 * "website-contacto"} persists that exact value.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void persistsTheGivenOrigemForAGeneralContactLead() throws Exception {
		InternalLeadRequest valid = validRequest();
		InternalLeadRequest generalContact =
				new InternalLeadRequest(
						valid.nome(), valid.email(), valid.telefone(), valid.mensagem(), null, null, null,
						"website-contacto");

		mockMvc
				.perform(
						post("/internal/leads")
								.header(TOKEN_HEADER, validToken)
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(generalContact)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.origem").value("website-contacto"));
	}
}

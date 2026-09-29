package pt.diamondcars.dcbobackend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import pt.diamondcars.dcbobackend.domain.car.Car;
import pt.diamondcars.dcbobackend.domain.car.CarRepository;
import pt.diamondcars.dcbobackend.domain.lead.Lead;
import pt.diamondcars.dcbobackend.domain.lead.LeadRepository;
import pt.diamondcars.dcbobackend.domain.lead.LeadStatus;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;
import pt.diamondcars.dcbobackend.web.dto.LeadRequest;

/**
 * End-to-end tests of {@link LeadController}, {@link pt.diamondcars.dcbobackend.service.LeadService}
 * and {@link ApiExceptionHandler} through the real servlet filter chain (TASK-010), using {@link
 * MockMvc} against a real PostgreSQL container ({@link AbstractPostgresIntegrationTest}), mirroring
 * the idiom {@code ClientControllerTest} (TASK-009) established.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LeadControllerTest extends AbstractPostgresIntegrationTest {

	private static final String USER_ROLE = "ROLE_USER";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private LeadRepository leadRepository;
	@Autowired private CarRepository carRepository;

	/**
	 * Clears every row this class writes before each test, for the same reason {@code
	 * CarControllerTest#cleanDatabase()} does (shared, JVM-wide singleton container, no per-test
	 * rollback).
	 */
	@BeforeEach
	void cleanDatabase() {
		leadRepository.deleteAll();
		carRepository.deleteAll();
	}

	private static Car.CarBuilder aPersistedCar() {
		return Car.builder()
				.marca("Audi")
				.modelo("A4")
				.ano(2019)
				.preco(new BigDecimal("22000.00"))
				.km(80000)
				.cor("Branco")
				.combustivel("Gasolina")
				.transmissao("Manual")
				.origem("stand");
	}

	private static LeadRequest validLeadRequest(UUID carroId) {
		return new LeadRequest(
				"Joao Cliente", "912345678", "joao@example.com", "Quer test drive", "contactado", null, carroId,
				"Audi", "A4", new BigDecimal("22000.00"));
	}

	/**
	 * Acceptance criterion 1 (positive control) and acceptance criterion 7 (first half): a valid
	 * {@code POST /api/leads} returns 201, and the created lead has {@code origem = "backoffice"}
	 * — not {@code "website"}, {@code leads.origem}'s database default ({@code V1__init.sql:118}).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void createsALeadAndReadsItBackByIdWithBackofficeOrigin() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());

		String location =
				mockMvc
						.perform(
								post("/api/leads")
										.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(validLeadRequest(car.getId()))))
						.andExpect(status().isCreated())
						.andExpect(header().string("Location", notNullValue()))
						.andExpect(jsonPath("$.nome").value("Joao Cliente"))
						.andExpect(jsonPath("$.origem").value("backoffice"))
						.andReturn()
						.getResponse()
						.getHeader("Location");

		mockMvc
				.perform(get(location).with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.carroId").value(car.getId().toString()));
	}

	/**
	 * Acceptance criterion 1: {@code POST /api/leads} without {@code nome} responds 400, naming the
	 * offending field.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsALeadWithoutNome() throws Exception {
		LeadRequest valid = validLeadRequest(null);
		LeadRequest withoutNome =
				new LeadRequest(
						"", valid.telefone(), valid.email(), valid.notas(), valid.status(), valid.followUpDate(),
						valid.carroId(), valid.carroMarca(), valid.carroModelo(), valid.carroPreco());

		mockMvc
				.perform(
						post("/api/leads")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(withoutNome)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("nome")));
	}

	/**
	 * IMPORTANTE 3 ({@code backlog/reviews/TASK-010-r1.md}): a {@code carroPreco} large enough to
	 * overflow the {@code numeric(12,2)} column is rejected with 400, naming the offending field,
	 * where it used to reach the database and surface as an unmapped 409 ({@code
	 * DataIntegrityViolationException} — the same pattern already fixed for {@code CarRequest}/{@code
	 * SellCarRequest} in TASK-008).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsALeadWithACarroPrecoLargeEnoughToOverflowTheDatabaseColumn() throws Exception {
		LeadRequest valid = validLeadRequest(null);
		LeadRequest tooExpensive =
				new LeadRequest(
						valid.nome(), valid.telefone(), valid.email(), valid.notas(), valid.status(),
						valid.followUpDate(), valid.carroId(), valid.carroMarca(), valid.carroModelo(),
						new BigDecimal("99999999999"));

		mockMvc
				.perform(
						post("/api/leads")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(tooExpensive)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("carroPreco")));
	}

	/**
	 * Acceptance criterion 2: {@code GET /api/leads?carroId=} returns only the lead about that car.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listFiltersLeadsByCarroId() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		Car otherCar = carRepository.saveAndFlush(aPersistedCar().modelo("A6").build());
		leadRepository.saveAndFlush(Lead.builder().nome("Lead A").telefone("911111111").car(car).build());
		leadRepository.saveAndFlush(Lead.builder().nome("Lead B").telefone("922222222").car(otherCar).build());
		leadRepository.saveAndFlush(Lead.builder().nome("Lead Sem Carro").telefone("933333333").build());

		mockMvc
				.perform(
						get("/api/leads?carroId={id}", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].nome").value("Lead A"));
	}

	/**
	 * {@code GET /api/leads?status=} accepts the lower-snake-case database value (not the Java
	 * constant name), and filters accordingly.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listFiltersLeadsByStatus() throws Exception {
		leadRepository.saveAndFlush(
				Lead.builder().nome("Vendido").telefone("911111111").status(LeadStatus.VENDIDO).build());
		leadRepository.saveAndFlush(
				Lead.builder().nome("Contactado").telefone("922222222").build());

		mockMvc
				.perform(
						get("/api/leads?status=vendido")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].nome").value("Vendido"));
	}

	/**
	 * A {@code PUT /api/leads/{id}} replaces the contact/pipeline fields but leaves the car snapshot
	 * untouched, mirroring {@code updateLead} ({@code dcbo/src/services/firebaseService.js:525-545}).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void updatingALeadReplacesContactFieldsButKeepsCarSnapshot() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		Lead lead =
				leadRepository.saveAndFlush(
						Lead.builder()
								.nome("Antigo")
								.telefone("911111111")
								.car(car)
								.carroMarca("Audi")
								.carroModelo("A4")
								.build());
		Car otherCar = carRepository.saveAndFlush(aPersistedCar().modelo("A6").build());
		LeadRequest update = validLeadRequest(otherCar.getId());

		mockMvc
				.perform(
						put("/api/leads/{id}", lead.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Joao Cliente"))
				.andExpect(jsonPath("$.carroId").value(car.getId().toString()))
				.andExpect(jsonPath("$.carroMarca").value("Audi"));
	}

	/**
	 * {@code DELETE /api/leads/{id}} removes the lead.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletingALeadRemovesIt() throws Exception {
		Lead lead = leadRepository.saveAndFlush(Lead.builder().nome("A Remover").telefone("911111111").build());

		mockMvc
				.perform(delete("/api/leads/{id}", lead.getId()).with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNoContent());

		assertThat(leadRepository.existsById(lead.getId())).isFalse();
	}

	/**
	 * {@code GET /api/leads/{id}} for an id that does not exist responds 404.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void returns404ForAnUnknownLeadId() throws Exception {
		mockMvc
				.perform(get("/api/leads/{id}", UUID.randomUUID()).with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNotFound());
	}

	/**
	 * {@code GET /api/leads} without an {@code Authorization} header responds 401 — the global rule
	 * from {@code SecurityConfig} (TASK-007), re-verified against this business endpoint.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listingLeadsWithoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/leads")).andExpect(status().isUnauthorized());
	}
}

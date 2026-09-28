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
import pt.diamondcars.dcbobackend.domain.partner.Partner;
import pt.diamondcars.dcbobackend.domain.partner.PartnerRepository;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;
import pt.diamondcars.dcbobackend.web.dto.CarRequest;
import pt.diamondcars.dcbobackend.web.dto.PartnerRequest;
import pt.diamondcars.dcbobackend.web.dto.SellCarRequest;

/**
 * End-to-end tests of {@link PartnerController}, {@link
 * pt.diamondcars.dcbobackend.service.PartnerService}, the partner-related hooks {@link
 * pt.diamondcars.dcbobackend.service.CarService} calls, and {@link ApiExceptionHandler} through
 * the real servlet filter chain (TASK-009), using {@link MockMvc} against a real PostgreSQL
 * container ({@link AbstractPostgresIntegrationTest}), mirroring the idiom {@code
 * CarControllerTest} (TASK-008) established.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PartnerControllerTest extends AbstractPostgresIntegrationTest {

	private static final String USER_ROLE = "ROLE_USER";
	private static final String ADMIN_ROLE = "ROLE_ADMIN";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private PartnerRepository partnerRepository;
	@Autowired private CarRepository carRepository;

	/**
	 * Clears every row this class writes before each test, for the same reason {@code
	 * CarControllerTest#cleanDatabase()} does (shared, JVM-wide singleton container, no per-test
	 * rollback).
	 */
	@BeforeEach
	void cleanDatabase() {
		carRepository.deleteAll();
		partnerRepository.deleteAll();
	}

	private static PartnerRequest validPartnerRequest() {
		return new PartnerRequest("Parceiro Teste", "parceiro@example.com", "912345678", "Notas");
	}

	private static CarRequest consignmentCarRequestFor(UUID partnerId) {
		return new CarRequest(
				"Audi",
				"A4",
				2019,
				new BigDecimal("22000.00"),
				80000,
				"Branco",
				"Gasolina",
				"Manual",
				"stand",
				null,
				null,
				null,
				true,
				partnerId,
				new BigDecimal("500.00"),
				0,
				false,
				null,
				null);
	}

	private static Car.CarBuilder aConsignmentCarFor(Partner partner) {
		return Car.builder()
				.marca("Audi")
				.modelo("A4")
				.ano(2019)
				.preco(new BigDecimal("22000.00"))
				.km(80000)
				.cor("Branco")
				.combustivel("Gasolina")
				.transmissao("Manual")
				.origem("stand")
				.consignacao(true)
				.partner(partner)
				.commissionValue(new BigDecimal("500.00"));
	}

	/**
	 * Acceptance criterion 1: a valid {@code POST /api/partners} returns 201 with a {@code
	 * Location} header, and the created partner can then be read back with {@code
	 * GET /api/partners/{id}} (200).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void createsAPartnerAndReadsItBackById() throws Exception {
		String location =
				mockMvc
						.perform(
								post("/api/partners")
										.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(validPartnerRequest())))
						.andExpect(status().isCreated())
						.andExpect(header().string("Location", notNullValue()))
						.andExpect(jsonPath("$.name").value("Parceiro Teste"))
						.andExpect(jsonPath("$.carsCount").value(0))
						.andExpect(jsonPath("$.totalCommission").value(0))
						.andReturn()
						.getResponse()
						.getHeader("Location");

		mockMvc
				.perform(get(location).with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("parceiro@example.com"));
	}

	/**
	 * A partner name with only 2 characters (below the 3-character floor {@code partners.js:53-55}
	 * enforces) responds 400, naming the field.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsAPartnerWithATooShortName() throws Exception {
		PartnerRequest valid = validPartnerRequest();
		PartnerRequest invalid = new PartnerRequest("Jo", valid.email(), valid.phone(), valid.notes());

		mockMvc
				.perform(
						post("/api/partners")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(invalid)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("name")));
	}

	/**
	 * A partner may be created without a phone (unlike a client's, it is optional).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void acceptsAPartnerWithoutAPhone() throws Exception {
		PartnerRequest valid = validPartnerRequest();
		PartnerRequest withoutPhone = new PartnerRequest(valid.name(), valid.email(), "", valid.notes());

		mockMvc
				.perform(
						post("/api/partners")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(withoutPhone)))
				.andExpect(status().isCreated());
	}

	/**
	 * {@code GET /api/partners/{id}} for an id that does not exist responds 404.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void returns404ForAnUnknownPartnerId() throws Exception {
		mockMvc
				.perform(
						get("/api/partners/{id}", UUID.randomUUID())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNotFound());
	}

	/**
	 * A {@code PUT /api/partners/{id}} replaces every field with the given payload, without
	 * touching {@code carsCount}/{@code totalCommission}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void updatingAPartnerReplacesItsFieldsButNotItsCounters() throws Exception {
		Partner partner =
				partnerRepository.saveAndFlush(
						Partner.builder().name("Antigo").carsCount(2).totalCommission(new BigDecimal("100.00")).build());

		mockMvc
				.perform(
						put("/api/partners/{id}", partner.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(validPartnerRequest())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Parceiro Teste"))
				.andExpect(jsonPath("$.carsCount").value(2))
				.andExpect(jsonPath("$.totalCommission").value(100.00));
	}

	/**
	 * Requirement 5: creating a consignment car referencing a partner increments that partner's
	 * {@code cars_count}, within the same transaction as {@code POST /api/cars}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void creatingAConsignmentCarIncrementsThePartnersCarsCount() throws Exception {
		Partner partner = partnerRepository.saveAndFlush(Partner.builder().name("Parceiro A").build());

		mockMvc
				.perform(
						post("/api/cars")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(consignmentCarRequestFor(partner.getId()))))
				.andExpect(status().isCreated());

		mockMvc
				.perform(
						get("/api/partners/{id}", partner.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.carsCount").value(1));
	}

	/**
	 * Counterpart of {@link #creatingAConsignmentCarIncrementsThePartnersCarsCount()}: deleting that
	 * consignment car decrements {@code cars_count} back down.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletingAConsignmentCarDecrementsThePartnersCarsCount() throws Exception {
		Partner partner =
				partnerRepository.saveAndFlush(Partner.builder().name("Parceiro B").carsCount(1).build());
		Car car = carRepository.saveAndFlush(aConsignmentCarFor(partner).build());

		mockMvc
				.perform(
						delete("/api/cars/{id}", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE))))
				.andExpect(status().isNoContent());

		mockMvc
				.perform(
						get("/api/partners/{id}", partner.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.carsCount").value(0));
	}

	/**
	 * Acceptance criterion 4: selling a consignment car increments the partner's {@code
	 * total_commission} by the car's {@code commissionValue}, within the same transaction as
	 * {@code POST /api/cars/{id}/sell}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void sellingAConsignmentCarIncrementsThePartnersTotalCommission() throws Exception {
		Partner partner = partnerRepository.saveAndFlush(Partner.builder().name("Parceiro C").build());
		Car car = carRepository.saveAndFlush(aConsignmentCarFor(partner).build());
		SellCarRequest sellRequest = new SellCarRequest(new BigDecimal("21000.00"), null);

		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(sellRequest)))
				.andExpect(status().isOk());

		assertThat(partnerRepository.findById(partner.getId()).orElseThrow().getTotalCommission())
				.isEqualByComparingTo(new BigDecimal("500.00"));

		mockMvc
				.perform(
						post("/api/cars/{id}/revert-sale", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk());

		assertThat(partnerRepository.findById(partner.getId()).orElseThrow().getTotalCommission())
				.isEqualByComparingTo(BigDecimal.ZERO);
	}

	/**
	 * Acceptance criterion 5: {@code GET /api/partners/{id}/cars} returns only cars with that
	 * exact {@code partner_id}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listsOnlyTheCarsConsignedByThatPartner() throws Exception {
		Partner partner = partnerRepository.saveAndFlush(Partner.builder().name("Parceiro D").build());
		Partner other = partnerRepository.saveAndFlush(Partner.builder().name("Parceiro E").build());
		Car ownCar = carRepository.saveAndFlush(aConsignmentCarFor(partner).build());
		carRepository.saveAndFlush(aConsignmentCarFor(other).modelo("Other").build());
		carRepository.saveAndFlush(
				Car.builder()
						.marca("BMW")
						.modelo("Standalone")
						.ano(2020)
						.preco(new BigDecimal("15000.00"))
						.km(1000)
						.cor("Preto")
						.combustivel("Diesel")
						.transmissao("Manual")
						.origem("stand")
						.build());

		mockMvc
				.perform(
						get("/api/partners/{id}/cars", partner.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(ownCar.getId().toString()));
	}

	/**
	 * Deleting a partner that still has a consignment car associated responds 409, and the partner
	 * still exists afterwards — mirrors {@code
	 * ClientControllerTest#deletingAClientWithACarAssociatedIsRejectedWith409()}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletingAPartnerWithAConsignmentCarAssociatedIsRejectedWith409() throws Exception {
		Partner partner = partnerRepository.saveAndFlush(Partner.builder().name("Parceiro F").build());
		carRepository.saveAndFlush(aConsignmentCarFor(partner).build());

		mockMvc
				.perform(
						delete("/api/partners/{id}", partner.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));

		assertThat(partnerRepository.existsById(partner.getId())).isTrue();
	}

	/**
	 * Counterpart of {@link #deletingAPartnerWithAConsignmentCarAssociatedIsRejectedWith409()}: a
	 * partner without any car succeeds (204).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletingAPartnerWithoutCarsSucceeds() throws Exception {
		Partner partner = partnerRepository.saveAndFlush(Partner.builder().name("Parceiro G").build());

		mockMvc
				.perform(
						delete("/api/partners/{id}", partner.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNoContent());

		assertThat(partnerRepository.existsById(partner.getId())).isFalse();
	}

	/**
	 * Acceptance criterion 6: {@code GET /api/partners} without an {@code Authorization} header
	 * responds 401 — the global rule from {@code SecurityConfig} (TASK-007), re-verified against
	 * this business endpoint.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listingPartnersWithoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/partners")).andExpect(status().isUnauthorized());
	}
}

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
	 * IMPORTANTE 3 ({@code backlog/reviews/TASK-009-r1.md}): {@code cars_count} only changes on a
	 * sale, mirroring {@code dcbo/src/App.js:351} ({@code incrementPartnerCars} is only ever called
	 * from inside {@code sellCar}) — creating a consignment car must not touch it.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void creatingAConsignmentCarDoesNotChangeThePartnersCarsCount() throws Exception {
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
				.andExpect(jsonPath("$.carsCount").value(0));
	}

	/**
	 * IMPORTANTE 3 ({@code backlog/reviews/TASK-009-r1.md}): counterpart of {@link
	 * #creatingAConsignmentCarDoesNotChangeThePartnersCarsCount()} — deleting an unsold consignment
	 * car must not touch {@code cars_count} either.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletingAnUnsoldConsignmentCarDoesNotChangeThePartnersCarsCount() throws Exception {
		Partner partner =
				partnerRepository.saveAndFlush(Partner.builder().name("Parceiro B").carsCount(3).build());
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
				.andExpect(jsonPath("$.carsCount").value(3));
	}

	/**
	 * Acceptance criterion 4 / IMPORTANTE 3 ({@code backlog/reviews/TASK-009-r1.md}): selling a
	 * consignment car increments the partner's {@code total_commission} by the car's {@code
	 * commissionValue} <em>and</em> {@code cars_count} by one, within the same transaction as
	 * {@code POST /api/cars/{id}/sell}; reverting the sale brings both back down.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void sellingAConsignmentCarIncrementsThePartnersTotalCommissionAndCarsCount() throws Exception {
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

		Partner afterSale = partnerRepository.findById(partner.getId()).orElseThrow();
		assertThat(afterSale.getTotalCommission()).isEqualByComparingTo(new BigDecimal("500.00"));
		assertThat(afterSale.getCarsCount()).isEqualTo(1);

		mockMvc
				.perform(
						post("/api/cars/{id}/revert-sale", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk());

		Partner afterRevert = partnerRepository.findById(partner.getId()).orElseThrow();
		assertThat(afterRevert.getTotalCommission()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(afterRevert.getCarsCount()).isEqualTo(0);
	}

	/**
	 * BLOQUEADOR 1 ({@code backlog/reviews/TASK-009-r1.md}): {@code revert-sale} on a consignment
	 * car that was never sold must be a total no-op — in particular it must never discount the
	 * partner's {@code total_commission}/{@code cars_count}, which before this fix happened
	 * unconditionally.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void revertingASaleOnANeverSoldConsignmentCarLeavesThePartnersCountersUntouched() throws Exception {
		Partner partner =
				partnerRepository.saveAndFlush(
						Partner.builder()
								.name("Parceiro H")
								.carsCount(3)
								.totalCommission(new BigDecimal("1500.00"))
								.build());
		Car car = carRepository.saveAndFlush(aConsignmentCarFor(partner).build());

		mockMvc
				.perform(
						post("/api/cars/{id}/revert-sale", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk());

		Partner reloaded = partnerRepository.findById(partner.getId()).orElseThrow();
		assertThat(reloaded.getTotalCommission()).isEqualByComparingTo(new BigDecimal("1500.00"));
		assertThat(reloaded.getCarsCount()).isEqualTo(3);
	}

	/**
	 * BLOQUEADOR 1 ({@code backlog/reviews/TASK-009-r1.md}): calling {@code revert-sale} a second
	 * time on a car whose sale was already reverted must not discount the partner's {@code
	 * total_commission}/{@code cars_count} again — measured by the reviewer as a real double
	 * discount (1500 -> 1100 -> 100 on two reverts of the same sale).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void revertingAnAlreadyRevertedSaleDoesNotDiscountThePartnersCountersAgain() throws Exception {
		Partner partner =
				partnerRepository.saveAndFlush(
						Partner.builder()
								.name("Parceiro I")
								.carsCount(2)
								.totalCommission(new BigDecimal("1000.00"))
								.build());
		Car car = carRepository.saveAndFlush(aConsignmentCarFor(partner).build());
		SellCarRequest sellRequest = new SellCarRequest(new BigDecimal("21000.00"), null);

		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(sellRequest)))
				.andExpect(status().isOk());

		Partner afterSale = partnerRepository.findById(partner.getId()).orElseThrow();
		assertThat(afterSale.getTotalCommission()).isEqualByComparingTo(new BigDecimal("1500.00"));
		assertThat(afterSale.getCarsCount()).isEqualTo(3);

		mockMvc
				.perform(
						post("/api/cars/{id}/revert-sale", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk());

		Partner afterFirstRevert = partnerRepository.findById(partner.getId()).orElseThrow();
		assertThat(afterFirstRevert.getTotalCommission()).isEqualByComparingTo(new BigDecimal("1000.00"));
		assertThat(afterFirstRevert.getCarsCount()).isEqualTo(2);

		mockMvc
				.perform(
						post("/api/cars/{id}/revert-sale", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk());

		Partner afterSecondRevert = partnerRepository.findById(partner.getId()).orElseThrow();
		assertThat(afterSecondRevert.getTotalCommission()).isEqualByComparingTo(new BigDecimal("1000.00"));
		assertThat(afterSecondRevert.getCarsCount()).isEqualTo(2);
	}

	/**
	 * IMPORTANTE 2 ({@code backlog/reviews/TASK-009-r1.md}): editing a <em>sold</em> consignment
	 * car's {@code partnerId} via {@code PUT /api/cars/{id}} must move the registered commission
	 * from the old partner to the new one, so that a later {@code revert-sale} undoes it from the
	 * right place instead of leaving it stuck on the original partner forever.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void editingASoldConsignmentCarsPartnerMovesTheCommissionToTheNewPartner() throws Exception {
		Partner partnerA = partnerRepository.saveAndFlush(Partner.builder().name("Parceiro J").build());
		Partner partnerB = partnerRepository.saveAndFlush(Partner.builder().name("Parceiro K").build());
		Car car = carRepository.saveAndFlush(aConsignmentCarFor(partnerA).build());
		SellCarRequest sellRequest = new SellCarRequest(new BigDecimal("21000.00"), null);

		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(sellRequest)))
				.andExpect(status().isOk());
		assertThat(partnerRepository.findById(partnerA.getId()).orElseThrow().getTotalCommission())
				.isEqualByComparingTo(new BigDecimal("500.00"));

		mockMvc
				.perform(
						put("/api/cars/{id}", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(consignmentCarRequestFor(partnerB.getId()))))
				.andExpect(status().isOk());

		assertThat(partnerRepository.findById(partnerA.getId()).orElseThrow().getTotalCommission())
				.isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(partnerRepository.findById(partnerA.getId()).orElseThrow().getCarsCount()).isEqualTo(0);
		assertThat(partnerRepository.findById(partnerB.getId()).orElseThrow().getTotalCommission())
				.isEqualByComparingTo(new BigDecimal("500.00"));
		assertThat(partnerRepository.findById(partnerB.getId()).orElseThrow().getCarsCount()).isEqualTo(1);

		mockMvc
				.perform(
						post("/api/cars/{id}/revert-sale", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk());

		assertThat(partnerRepository.findById(partnerB.getId()).orElseThrow().getTotalCommission())
				.isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(partnerRepository.findById(partnerB.getId()).orElseThrow().getCarsCount()).isEqualTo(0);
		assertThat(partnerRepository.findById(partnerA.getId()).orElseThrow().getTotalCommission())
				.isEqualByComparingTo(BigDecimal.ZERO);
	}

	/**
	 * IMPORTANTE 4 ({@code backlog/reviews/TASK-009-r1.md}): phone numbers with spaces that the
	 * {@code dcbo} partner form accepts today (it strips spaces before validating, {@code
	 * partners.js:61}) must not be rejected with 400 here.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void acceptsAPartnerPhoneWithSpacesLikeTheDcboFormDoes() throws Exception {
		PartnerRequest valid = validPartnerRequest();
		PartnerRequest spacedLocal =
				new PartnerRequest(valid.name(), valid.email(), "912 345 678", valid.notes());
		PartnerRequest spacedInternational =
				new PartnerRequest(valid.name(), valid.email(), "+351 912 345 678", valid.notes());

		mockMvc
				.perform(
						post("/api/partners")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(spacedLocal)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.phone").value("912345678"));

		mockMvc
				.perform(
						post("/api/partners")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(spacedInternational)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.phone").value("+351912345678"));
	}

	/**
	 * Counterpart of {@link #acceptsAPartnerPhoneWithSpacesLikeTheDcboFormDoes()}: a phone that does
	 * not match {@code PHONE_PT} even with spaces stripped is still rejected with 400.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsAPartnerPhoneThatIsInvalidEvenWithoutSpaces() throws Exception {
		PartnerRequest valid = validPartnerRequest();
		PartnerRequest invalid = new PartnerRequest(valid.name(), valid.email(), "812345678", valid.notes());

		mockMvc
				.perform(
						post("/api/partners")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(invalid)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("phone")));
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

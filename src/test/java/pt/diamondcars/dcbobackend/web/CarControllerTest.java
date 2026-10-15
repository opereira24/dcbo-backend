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
import java.math.BigDecimal;
import java.util.List;
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
import pt.diamondcars.dcbobackend.domain.client.Client;
import pt.diamondcars.dcbobackend.domain.client.ClientRepository;
import pt.diamondcars.dcbobackend.domain.partner.Partner;
import pt.diamondcars.dcbobackend.domain.partner.PartnerRepository;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;
import pt.diamondcars.dcbobackend.web.dto.CarRequest;
import pt.diamondcars.dcbobackend.web.dto.HighlightRequest;
import pt.diamondcars.dcbobackend.web.dto.SellCarRequest;

/**
 * End-to-end tests of {@link CarController}, {@link pt.diamondcars.dcbobackend.service.CarService}
 * and {@link ApiExceptionHandler} through the real servlet filter chain (TASK-008, requirement 7),
 * using {@link MockMvc} against a real PostgreSQL container ({@link AbstractPostgresIntegrationTest},
 * TASK-001). Every authenticated request uses the {@code jwt()} post-processor to inject an
 * already-authenticated principal with the given authorities directly, since the decode/claims
 * conversion path itself is already fixed end-to-end by {@code SecurityConfigTest} (TASK-007) and
 * does not need to be re-proven here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CarControllerTest extends AbstractPostgresIntegrationTest {

	private static final String ADMIN_ROLE = "ROLE_ADMIN";
	private static final String USER_ROLE = "ROLE_USER";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private CarRepository carRepository;
	@Autowired private ClientRepository clientRepository;
	@Autowired private PartnerRepository partnerRepository;

	/**
	 * Clears every row this class writes before each test, so a car/client left behind by one test
	 * (e.g. the 8 featured cars a highlight test creates) can never influence another. The
	 * {@link AbstractPostgresIntegrationTest} container is a JVM-wide singleton with no per-test
	 * rollback, so without this, state leaked across tests — the pre-existing {@code
	 * listsCarsMostRecentlyCreatedFirstByDefault} test needed a {@code ?size=200} workaround for
	 * exactly this reason (IMPORTANTE 6, {@code backlog/reviews/TASK-008-r1.md}).
	 */
	@BeforeEach
	void cleanDatabase() {
		carRepository.deleteAll();
		clientRepository.deleteAll();
		partnerRepository.deleteAll();
	}

	private static CarRequest validCarRequest() {
		return new CarRequest(
				"BMW",
				"320d",
				2020,
				new BigDecimal("25000.00"),
				50000,
				"Preto",
				"Diesel",
				"Automatica",
				"stand",
				"Carro em otimo estado",
				new BigDecimal("20000.00"),
				null,
				false,
				null,
				null,
				12,
				false,
				List.of("https://img/1.jpg", "https://img/2.jpg"),
				List.of("https://img/1-thumb.jpg", "https://img/2-thumb.jpg"));
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

	/**
	 * Acceptance criterion 1: a valid {@code POST /api/cars} returns 201 with a {@code Location}
	 * header, and the created car can then be read back with {@code GET /api/cars/{id}} (200).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void createsACarAndReadsItBackById() throws Exception {
		String location =
				mockMvc
						.perform(
								post("/api/cars")
										.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(validCarRequest())))
						.andExpect(status().isCreated())
						.andExpect(header().string("Location", notNullValue()))
						.andExpect(jsonPath("$.marca").value("BMW"))
						.andExpect(jsonPath("$.images[0]").value("https://img/1.jpg"))
						.andReturn()
						.getResponse()
						.getHeader("Location");

		mockMvc
				.perform(get(location).with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modelo").value("320d"));
	}

	/**
	 * Acceptance criterion 2: {@code POST /api/cars} with {@code preco = 50} (below the 100 floor)
	 * responds 400, and the error body names the offending field.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsACarWithAPriceBelowTheFloor() throws Exception {
		CarRequest valid = validCarRequest();
		CarRequest invalid =
				new CarRequest(
						valid.marca(),
						valid.modelo(),
						valid.ano(),
						new BigDecimal("50"),
						valid.km(),
						valid.cor(),
						valid.combustivel(),
						valid.transmissao(),
						valid.origem(),
						valid.descricao(),
						valid.precoCompra(),
						valid.dataCompra(),
						valid.isConsignacao(),
						valid.partnerId(),
						valid.commissionValue(),
						valid.garantiaMeses(),
						valid.destaque(),
						valid.images(),
						valid.imageThumbnails());

		mockMvc
				.perform(
						post("/api/cars")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(invalid)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("preco")));
	}

	/**
	 * {@code GET /api/cars/{id}} for an id that does not exist responds 404 (TASK-008 requirement
	 * 7).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void returns404ForAnUnknownCarId() throws Exception {
		mockMvc
				.perform(
						get("/api/cars/{id}", UUID.randomUUID())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNotFound());
	}

	/**
	 * Acceptance criterion 3: selling a car to a known client increments that client's {@code
	 * purchases_count} within the same request, and reverting the sale decrements it back —
	 * exercised through the real service transaction, not by inspecting the entity directly.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void sellingACarIncrementsAndRevertingDecrementsTheClientsPurchaseCount() throws Exception {
		Client client =
				clientRepository.saveAndFlush(
						Client.builder().name("Cliente Teste").phone("912345678").build());
		Car car = carRepository.saveAndFlush(aPersistedCar().build());

		SellCarRequest sellRequest = new SellCarRequest(new BigDecimal("21000.00"), client.getId());

		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(sellRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.vendido").value(true))
				.andExpect(jsonPath("$.dataVenda").value(notNullValue()))
				.andExpect(jsonPath("$.clienteId").value(client.getId().toString()));

		assertThat(clientRepository.findById(client.getId()).orElseThrow().getPurchasesCount()).isEqualTo(1);

		mockMvc
				.perform(
						post("/api/cars/{id}/revert-sale", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.vendido").value(false))
				.andExpect(jsonPath("$.clienteId").doesNotExist());

		assertThat(clientRepository.findById(client.getId()).orElseThrow().getPurchasesCount()).isEqualTo(0);
	}

	/**
	 * Reserving and then releasing a car flips {@code reservado} accordingly (TASK-008 requirement
	 * 7).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void reservesAndReleasesACar() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());

		mockMvc
				.perform(
						post("/api/cars/{id}/reserve", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reservado").value(true));

		mockMvc
				.perform(
						post("/api/cars/{id}/release-reservation", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reservado").value(false));
	}

	/**
	 * Acceptance criterion 4: once 8 cars are already featured, marking a 9th as featured through
	 * {@code PATCH /api/cars/{id}/highlight} responds 409.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsFeaturingAWinthCarWhenTheLimitOfEightIsAlreadyReached() throws Exception {
		for (int i = 0; i < 8; i++) {
			carRepository.saveAndFlush(aPersistedCar().modelo("Featured " + i).destaque(true).build());
		}
		Car ninthCar = carRepository.saveAndFlush(aPersistedCar().modelo("Ninth").build());

		mockMvc
				.perform(
						patch("/api/cars/{id}/highlight", ninthCar.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(new HighlightRequest(true))))
				.andExpect(status().isConflict());
	}

	/**
	 * IMPORTANTE 3 ({@code backlog/reviews/TASK-008-r1.md}): the positive control missing from the
	 * test above — featuring the 8th car (with only 7 already featured) must succeed with 200. Only
	 * together with the negative case (9th car, 8 already featured, rejected above) does this fix
	 * the limit at exactly 8: a mutation of {@code HIGHLIGHT_LIMIT} to 1 previously left the whole
	 * suite green because no test distinguished a limit of 8 from a limit of 1.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void allowsFeaturingExactlyTheEighthCar() throws Exception {
		for (int i = 0; i < 7; i++) {
			carRepository.saveAndFlush(aPersistedCar().modelo("Featured " + i).destaque(true).build());
		}
		Car eighthCar = carRepository.saveAndFlush(aPersistedCar().modelo("Eighth").build());

		mockMvc
				.perform(
						patch("/api/cars/{id}/highlight", eighthCar.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(new HighlightRequest(true))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.destaque").value(true));
	}

	/**
	 * IMPORTANTE 3 ({@code backlog/reviews/TASK-008-r1.md}): un-featuring a car ({@code destaque:
	 * false}) frees a slot for another one to be featured, exercising the {@code
	 * alreadyFeatured}/"already at the limit but not adding a new one" branch of {@code
	 * CarService#assertHighlightLimitRespected} that was previously never covered by any test.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void unfeaturingACarFreesUpASlotForAnother() throws Exception {
		Car firstFeatured = null;
		for (int i = 0; i < 8; i++) {
			Car featured =
					carRepository.saveAndFlush(aPersistedCar().modelo("Featured " + i).destaque(true).build());
			if (i == 0) {
				firstFeatured = featured;
			}
		}
		Car waiting = carRepository.saveAndFlush(aPersistedCar().modelo("Waiting").build());

		mockMvc
				.perform(
						patch("/api/cars/{id}/highlight", firstFeatured.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(new HighlightRequest(false))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.destaque").value(false));

		mockMvc
				.perform(
						patch("/api/cars/{id}/highlight", waiting.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(new HighlightRequest(true))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.destaque").value(true));
	}

	/**
	 * Acceptance criterion 5: {@code GET /api/cars} without an {@code Authorization} header responds
	 * 401 — the global rule from {@code SecurityConfig} (TASK-007), re-verified here against a real
	 * business endpoint rather than only the test-only probe {@code SecurityConfigTest} uses.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listingCarsWithoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/cars")).andExpect(status().isUnauthorized());
	}

	/**
	 * Acceptance criterion 6: {@code GET /api/cars} returns cars ordered by {@code createdAt}
	 * descending by default.
	 *
	 * <p>No longer needs a {@code ?size=200} workaround to dodge state left over by earlier tests
	 * (IMPORTANTE 6, {@code backlog/reviews/TASK-008-r1.md}): {@link #cleanDatabase()} guarantees
	 * these are the only 3 cars in the database when this runs.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listsCarsMostRecentlyCreatedFirstByDefault() throws Exception {
		Car first = carRepository.saveAndFlush(aPersistedCar().modelo("First").build());
		Car second = carRepository.saveAndFlush(aPersistedCar().modelo("Second").build());
		Car third = carRepository.saveAndFlush(aPersistedCar().modelo("Third").build());

		mockMvc
				.perform(get("/api/cars").with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(3))
				.andExpect(jsonPath("$.content[0].id").value(third.getId().toString()))
				.andExpect(jsonPath("$.content[1].id").value(second.getId().toString()))
				.andExpect(jsonPath("$.content[2].id").value(first.getId().toString()));
	}

	/**
	 * Requirement 1 / IMPORTANTE 2 ({@code backlog/reviews/TASK-008-r1.md}): {@code ?vendido=}
	 * filters the listing to cars with exactly that value, treating {@code false} as a real filter
	 * (not "no filter") — the case the review flagged as untested and most likely to hide an {@code
	 * if (vendido != null)} accidentally narrowed to {@code if (Boolean.TRUE.equals(vendido))}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void filtersTheListingByVendido() throws Exception {
		Car sold = carRepository.saveAndFlush(aPersistedCar().modelo("Sold").vendido(true).build());
		Car available = carRepository.saveAndFlush(aPersistedCar().modelo("Available").vendido(false).build());

		mockMvc
				.perform(
						get("/api/cars?vendido=true")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(sold.getId().toString()));

		mockMvc
				.perform(
						get("/api/cars?vendido=false")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(available.getId().toString()));
	}

	/**
	 * Requirement 1 / IMPORTANTE 2 ({@code backlog/reviews/TASK-008-r1.md}): two filters given
	 * together are combined with AND, which is the entire reason {@link
	 * pt.diamondcars.dcbobackend.service.CarSpecifications} exists instead of one derived-query
	 * method per filter.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void combinesTheVendidoAndDestaqueFiltersWithAnd() throws Exception {
		Car match =
				carRepository.saveAndFlush(
						aPersistedCar().modelo("Match").vendido(false).destaque(true).build());
		carRepository.saveAndFlush(
				aPersistedCar().modelo("SoldAndFeatured").vendido(true).destaque(true).build());
		carRepository.saveAndFlush(
				aPersistedCar().modelo("AvailableNotFeatured").vendido(false).destaque(false).build());

		mockMvc
				.perform(
						get("/api/cars?vendido=false&destaque=true")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(match.getId().toString()));
	}

	/**
	 * Regression test for {@code backlog/reviews/TASK-007-r2.md} (nota 3 ao planner): proves {@code
	 * @EnableMethodSecurity} (TASK-007) is actually exercised — an authenticated caller without the
	 * {@code ADMIN} role is rejected with 403 when deleting a car, not just any unauthenticated
	 * caller with 401. This is the only "wrong role" test in the module, and its counterpart below
	 * proves the same request succeeds for an {@code ADMIN}, so the 403 is caused by the role check
	 * and not by some other failure.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletingACarWithoutTheAdminRoleIsForbidden() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());

		mockMvc
				.perform(
						delete("/api/cars/{id}", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));

		assertThat(carRepository.existsById(car.getId())).isTrue();
	}

	/**
	 * Counterpart of {@link #deletingACarWithoutTheAdminRoleIsForbidden()}: the same request
	 * succeeds (204) for a caller with {@code ROLE_ADMIN}, proving the 403 above is specifically
	 * about the missing role and not, say, a broken mapping or a car that does not exist.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletingACarWithTheAdminRoleSucceeds() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());

		mockMvc
				.perform(
						delete("/api/cars/{id}", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE))))
				.andExpect(status().isNoContent());

		assertThat(carRepository.existsById(car.getId())).isFalse();
	}

	/**
	 * A {@code PUT /api/cars/{id}} replaces a car's photos with the ones given, in order, dropping
	 * any not resent (TASK-008 requirement 6).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void updatingACarReplacesItsImages() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		car.addImage(pt.diamondcars.dcbobackend.domain.car.CarImage.builder().url("https://img/old.jpg").position(0).build());
		carRepository.saveAndFlush(car);

		CarRequest update = validCarRequest();

		mockMvc
				.perform(
						put("/api/cars/{id}", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.images.length()").value(2))
				.andExpect(jsonPath("$.images[0]").value("https://img/1.jpg"))
				.andExpect(jsonPath("$.images[1]").value("https://img/2.jpg"));
	}

	/**
	 * BLOQUEADOR 1 ({@code backlog/reviews/TASK-008-r1.md}): selling a car that is already marked
	 * as sold is rejected with 409 instead of silently re-applying the sale — proven here for the
	 * worst case the review measured, a second {@code sell} call that omits {@code clienteId},
	 * which used to detach the car from its original buyer without decrementing anything, leaving
	 * that client's {@code purchases_count} inflated forever.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void sellingAnAlreadySoldCarIsRejectedAndLeavesTheOriginalSaleUntouched() throws Exception {
		Client client =
				clientRepository.saveAndFlush(
						Client.builder().name("Cliente A").phone("911111111").build());
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		SellCarRequest firstSale = new SellCarRequest(new BigDecimal("21000.00"), client.getId());

		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(firstSale)))
				.andExpect(status().isOk());

		SellCarRequest secondSaleWithoutAClient = new SellCarRequest(new BigDecimal("19000.00"), null);
		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(secondSaleWithoutAClient)))
				.andExpect(status().isConflict());

		mockMvc
				.perform(
						get("/api/cars/{id}", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(jsonPath("$.clienteId").value(client.getId().toString()))
				.andExpect(jsonPath("$.precoVenda").value(21000.00));
		assertThat(clientRepository.findById(client.getId()).orElseThrow().getPurchasesCount())
				.isEqualTo(1);
	}

	/**
	 * BLOQUEADOR 1 ({@code backlog/reviews/TASK-008-r1.md}): selling the same car twice to the same
	 * client — the simplest reproduction the review measured — does not double-count the purchase.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void sellingTheSameCarTwiceToTheSameClientDoesNotDoubleCountThePurchase() throws Exception {
		Client client =
				clientRepository.saveAndFlush(
						Client.builder().name("Cliente B").phone("922222222").build());
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		SellCarRequest sellRequest = new SellCarRequest(new BigDecimal("21000.00"), client.getId());

		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(sellRequest)))
				.andExpect(status().isOk());

		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(sellRequest)))
				.andExpect(status().isConflict());

		assertThat(clientRepository.findById(client.getId()).orElseThrow().getPurchasesCount())
				.isEqualTo(1);
	}

	/**
	 * BLOQUEADOR 2 ({@code backlog/reviews/TASK-008-r1.md}): an unknown {@code partnerId} in {@code
	 * POST /api/cars} responds 404, instead of a foreign-key-violation 500 surfacing at flush time.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void creatingACarWithAnUnknownPartnerIdIsRejectedWith404() throws Exception {
		CarRequest valid = validCarRequest();
		CarRequest withUnknownPartner =
				new CarRequest(
						valid.marca(),
						valid.modelo(),
						valid.ano(),
						valid.preco(),
						valid.km(),
						valid.cor(),
						valid.combustivel(),
						valid.transmissao(),
						valid.origem(),
						valid.descricao(),
						valid.precoCompra(),
						valid.dataCompra(),
						true,
						UUID.randomUUID(),
						valid.commissionValue(),
						valid.garantiaMeses(),
						valid.destaque(),
						valid.images(),
						valid.imageThumbnails());

		mockMvc
				.perform(
						post("/api/cars")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(withUnknownPartner)))
				.andExpect(status().isNotFound());
	}

	/**
	 * BLOQUEADOR 2 counterpart ({@code backlog/reviews/TASK-008-r1.md}): a real {@code partnerId}
	 * is accepted (201) and echoed back in the response, the positive control on the same axis as
	 * {@link #creatingACarWithAnUnknownPartnerIdIsRejectedWith404()} so that 404 is known to come
	 * specifically from an unknown id and not some unrelated failure.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void creatingACarWithARealPartnerIncludesItsIdInTheResponse() throws Exception {
		Partner partner =
				partnerRepository.saveAndFlush(Partner.builder().name("Parceiro Teste").build());
		CarRequest valid = validCarRequest();
		CarRequest withPartner =
				new CarRequest(
						valid.marca(),
						valid.modelo(),
						valid.ano(),
						valid.preco(),
						valid.km(),
						valid.cor(),
						valid.combustivel(),
						valid.transmissao(),
						valid.origem(),
						valid.descricao(),
						valid.precoCompra(),
						valid.dataCompra(),
						true,
						partner.getId(),
						valid.commissionValue(),
						valid.garantiaMeses(),
						valid.destaque(),
						valid.images(),
						valid.imageThumbnails());

		mockMvc
				.perform(
						post("/api/cars")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(withPartner)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.partnerId").value(partner.getId().toString()));
	}

	/**
	 * IMPORTANTE 1 ({@code backlog/reviews/TASK-008-r1.md}): a model year far in the future (the
	 * browser's dynamic {@code LIMITS.YEAR_MAX}) is rejected with 400, where it used to be accepted
	 * with 201.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsACarWithAModelYearTooFarInTheFuture() throws Exception {
		CarRequest valid = validCarRequest();
		CarRequest invalid =
				new CarRequest(
						valid.marca(),
						valid.modelo(),
						9999,
						valid.preco(),
						valid.km(),
						valid.cor(),
						valid.combustivel(),
						valid.transmissao(),
						valid.origem(),
						valid.descricao(),
						valid.precoCompra(),
						valid.dataCompra(),
						valid.isConsignacao(),
						valid.partnerId(),
						valid.commissionValue(),
						valid.garantiaMeses(),
						valid.destaque(),
						valid.images(),
						valid.imageThumbnails());

		mockMvc
				.perform(
						post("/api/cars")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(invalid)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("ano")));
	}

	/**
	 * IMPORTANTE 1 ({@code backlog/reviews/TASK-008-r1.md}): a price above the browser's {@code
	 * LIMITS.PRICE_MAX} (10 million) is rejected with 400, where it used to be accepted with 201.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsACarWithAPriceAboveTheBusinessCeiling() throws Exception {
		CarRequest valid = validCarRequest();
		CarRequest invalid =
				new CarRequest(
						valid.marca(),
						valid.modelo(),
						valid.ano(),
						new BigDecimal("50000000"),
						valid.km(),
						valid.cor(),
						valid.combustivel(),
						valid.transmissao(),
						valid.origem(),
						valid.descricao(),
						valid.precoCompra(),
						valid.dataCompra(),
						valid.isConsignacao(),
						valid.partnerId(),
						valid.commissionValue(),
						valid.garantiaMeses(),
						valid.destaque(),
						valid.images(),
						valid.imageThumbnails());

		mockMvc
				.perform(
						post("/api/cars")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(invalid)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("preco")));
	}

	/**
	 * IMPORTANTE 1 ({@code backlog/reviews/TASK-008-r1.md}): a price large enough to overflow the
	 * {@code numeric(12,2)} column is now caught by validation (400), where it used to reach the
	 * database and fail with an unmapped 500 ({@code numeric field overflow}).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsACarWithAPriceLargeEnoughToOverflowTheDatabaseColumn() throws Exception {
		CarRequest valid = validCarRequest();
		CarRequest invalid =
				new CarRequest(
						valid.marca(),
						valid.modelo(),
						valid.ano(),
						new BigDecimal("99999999999"),
						valid.km(),
						valid.cor(),
						valid.combustivel(),
						valid.transmissao(),
						valid.origem(),
						valid.descricao(),
						valid.precoCompra(),
						valid.dataCompra(),
						valid.isConsignacao(),
						valid.partnerId(),
						valid.commissionValue(),
						valid.garantiaMeses(),
						valid.destaque(),
						valid.images(),
						valid.imageThumbnails());

		mockMvc
				.perform(
						post("/api/cars")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(invalid)))
				.andExpect(status().isBadRequest());
	}

	/**
	 * IMPORTANTE 1 ({@code backlog/reviews/TASK-008-r1.md}): unsanitised markup in {@code marca} is
	 * rejected with 400, where it used to be accepted and echoed back verbatim — {@code marca}/
	 * {@code modelo}/{@code cor} are rendered by the public catalogue via TASK-017.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsACarWithUnsanitisedMarkupInMarca() throws Exception {
		CarRequest valid = validCarRequest();
		CarRequest invalid =
				new CarRequest(
						"<script>alert(1)</script>",
						valid.modelo(),
						valid.ano(),
						valid.preco(),
						valid.km(),
						valid.cor(),
						valid.combustivel(),
						valid.transmissao(),
						valid.origem(),
						valid.descricao(),
						valid.precoCompra(),
						valid.dataCompra(),
						valid.isConsignacao(),
						valid.partnerId(),
						valid.commissionValue(),
						valid.garantiaMeses(),
						valid.destaque(),
						valid.images(),
						valid.imageThumbnails());

		mockMvc
				.perform(
						post("/api/cars")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(invalid)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("marca")));
	}

	/**
	 * IMPORTANTE 4 ({@code backlog/reviews/TASK-008-r1.md}): a malformed JSON body responds 400 with
	 * the same {@code ApiError} envelope as every other mapped error, instead of Spring MVC's
	 * default body-less 400.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void malformedJsonBodyRespondsWithTheStandardErrorEnvelope() throws Exception {
		mockMvc
				.perform(
						post("/api/cars")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content("{not-valid-json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	/**
	 * IMPORTANTE 4 ({@code backlog/reviews/TASK-008-r1.md}): a non-UUID path variable responds 400
	 * with the same {@code ApiError} envelope, instead of Spring MVC's default body-less 400.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void aNonUuidPathVariableRespondsWithTheStandardErrorEnvelope() throws Exception {
		mockMvc
				.perform(
						get("/api/cars/not-a-uuid")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	/**
	 * IMPORTANTE 4 ({@code backlog/reviews/TASK-008-r1.md}): an unknown {@code ?sort=} property
	 * responds 400 with the standard {@code ApiError} envelope, instead of an unmapped {@code
	 * PropertyReferenceException} surfacing as 500.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void anUnknownSortPropertyRespondsWith400InsteadOf500() throws Exception {
		mockMvc
				.perform(
						get("/api/cars?sort=nosuchfield")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	/**
	 * BLOQUEADOR 1 ({@code backlog/reviews/TASK-008-r2.md}): a sub-path of {@code /api/cars} that no
	 * {@code @GetMapping} handles responds 404, not the 500 the generic {@code Exception.class}
	 * fallback used to produce by swallowing the {@code NoResourceFoundException} Spring MVC raises
	 * for an unmapped route before it ever reached {@code DefaultHandlerExceptionResolver}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void anUnmappedRouteInsideCarsRespondsWith404NotAGenericServerError() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());

		mockMvc
				.perform(
						get("/api/cars/{id}/does-not-exist", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	/**
	 * BLOQUEADOR 1 ({@code backlog/reviews/TASK-008-r2.md}): a request with a method no mapping on
	 * {@code /api/cars} accepts (only {@code GET}/{@code POST} are mapped there, never {@code
	 * DELETE}) responds 405, not the 500 the generic fallback used to produce by swallowing {@link
	 * org.springframework.web.HttpRequestMethodNotSupportedException}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void aMethodNotMappedOnCarsRespondsWith405NotAGenericServerError() throws Exception {
		mockMvc
				.perform(delete("/api/cars").with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE))))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.status").value(405));
	}

	/**
	 * BLOQUEADOR 1 ({@code backlog/reviews/TASK-008-r2.md}): {@code POST /api/cars} with a content
	 * type the API cannot read (it only accepts JSON) responds 415, not the 500 the generic
	 * fallback used to produce by swallowing {@link
	 * org.springframework.web.HttpMediaTypeNotSupportedException}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void anUnsupportedContentTypeOnCreateRespondsWith415NotAGenericServerError() throws Exception {
		mockMvc
				.perform(
						post("/api/cars")
								.with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_ROLE)))
								.contentType(MediaType.TEXT_PLAIN)
								.content("not json"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.status").value(415));
	}

	/**
	 * IMPORTANTE ({@code backlog/reviews/TASK-008-r2.md}): {@code SellCarRequest.precoVenda} above
	 * the same {@code CarRequest.PRICE_MAX_VALUE} ceiling the other monetary fields already enforce
	 * responds 400 and names the field, instead of reaching the {@code preco_venda numeric(12,2)}
	 * column and surfacing as a 409 "conflito" that misrepresents an input mistake as a data-state
	 * conflict.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void sellingACarAbovePriceCeilingIsRejectedWith400InsteadOf409() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		SellCarRequest tooExpensive = new SellCarRequest(new BigDecimal("99999999999"), null);

		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(tooExpensive)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("precoVenda")));
	}
}

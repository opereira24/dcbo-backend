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
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listsCarsMostRecentlyCreatedFirstByDefault() throws Exception {
		Car first = carRepository.saveAndFlush(aPersistedCar().modelo("First").build());
		Car second = carRepository.saveAndFlush(aPersistedCar().modelo("Second").build());
		Car third = carRepository.saveAndFlush(aPersistedCar().modelo("Third").build());

		mockMvc
				.perform(
						get("/api/cars?size=200")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(third.getId().toString()))
				.andExpect(jsonPath("$.content[1].id").value(second.getId().toString()))
				.andExpect(jsonPath("$.content[2].id").value(first.getId().toString()));
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
}

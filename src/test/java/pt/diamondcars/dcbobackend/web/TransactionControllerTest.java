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
import java.time.LocalDate;
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
import pt.diamondcars.dcbobackend.domain.transaction.Transaction;
import pt.diamondcars.dcbobackend.domain.transaction.TransactionRepository;
import pt.diamondcars.dcbobackend.domain.transaction.TransactionType;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;
import pt.diamondcars.dcbobackend.web.dto.SellCarRequest;
import pt.diamondcars.dcbobackend.web.dto.TransactionRequest;

/**
 * End-to-end tests of {@link TransactionController}/{@link FinanceController}, {@link
 * pt.diamondcars.dcbobackend.service.TransactionService}, {@link
 * pt.diamondcars.dcbobackend.service.CarService}'s transaction hooks, and {@link
 * ApiExceptionHandler} through the real servlet filter chain (TASK-011), using {@link MockMvc}
 * against a real PostgreSQL container ({@link AbstractPostgresIntegrationTest}), mirroring the
 * idiom {@code CarControllerTest} (TASK-008) established.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TransactionControllerTest extends AbstractPostgresIntegrationTest {

	private static final String USER_ROLE = "ROLE_USER";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private TransactionRepository transactionRepository;
	@Autowired private CarRepository carRepository;
	@Autowired private ClientRepository clientRepository;
	@Autowired private PartnerRepository partnerRepository;

	/**
	 * Clears every row this class writes before each test, for the same reason {@code
	 * CarControllerTest#cleanDatabase()} does (shared, JVM-wide singleton container, no per-test
	 * rollback).
	 */
	@BeforeEach
	void cleanDatabase() {
		transactionRepository.deleteAll();
		carRepository.deleteAll();
		clientRepository.deleteAll();
		partnerRepository.deleteAll();
	}

	private static TransactionRequest aRequest() {
		return new TransactionRequest(
				"despesa", new BigDecimal("150.00"), "Manutencao geral", "manutencao", "2026-02-10", null);
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
	 * Acceptance criterion 1: {@code valor = 0} responds 400 identifying {@code valor}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsATransactionWithAValorOfZero() throws Exception {
		TransactionRequest valid = aRequest();
		TransactionRequest invalid =
				new TransactionRequest(
						valid.tipo(), BigDecimal.ZERO, valid.descricao(), valid.categoria(), valid.data(), null);

		mockMvc
				.perform(
						post("/api/transactions")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(invalid)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("valor")));
	}

	/**
	 * Creates a transaction and reads it back by id (200), confirming {@code Location} is set on
	 * creation (201).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void createsATransactionAndReadsItBackById() throws Exception {
		String location =
				mockMvc
						.perform(
								post("/api/transactions")
										.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(aRequest())))
						.andExpect(status().isCreated())
						.andExpect(header().string("Location", notNullValue()))
						.andExpect(jsonPath("$.tipo").value("despesa"))
						.andExpect(jsonPath("$.systemGenerated").value(false))
						.andReturn()
						.getResponse()
						.getHeader("Location");

		mockMvc
				.perform(get(location).with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.descricao").value("Manutencao geral"));
	}

	/**
	 * {@code GET /api/transactions/{id}} for an id that does not exist responds 404.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void returns404ForAnUnknownTransactionId() throws Exception {
		mockMvc
				.perform(
						get("/api/transactions/{id}", UUID.randomUUID())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNotFound());
	}

	/**
	 * Requirement 2: an unknown {@code carroId} responds 400 (not 404 — deliberately named by the
	 * requirement text, unlike the 404 precedent for other "unknown referenced id" fields elsewhere
	 * in the API).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsATransactionWithAnUnknownCarroIdWith400() throws Exception {
		TransactionRequest valid = aRequest();
		TransactionRequest withUnknownCar =
				new TransactionRequest(
						valid.tipo(), valid.valor(), valid.descricao(), valid.categoria(), valid.data(), UUID.randomUUID());

		mockMvc
				.perform(
						post("/api/transactions")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(withUnknownCar)))
				.andExpect(status().isBadRequest());
	}

	/**
	 * Requirement 2 counterpart: a real {@code carroId} is accepted (201) and echoed back.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void acceptsATransactionWithARealCarroId() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		TransactionRequest valid = aRequest();
		TransactionRequest withCar =
				new TransactionRequest(
						valid.tipo(), valid.valor(), valid.descricao(), valid.categoria(), valid.data(), car.getId());

		mockMvc
				.perform(
						post("/api/transactions")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(withCar)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.carroId").value(car.getId().toString()));
	}

	/**
	 * Updates a transaction's business fields.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void updatesATransaction() throws Exception {
		Transaction saved =
				transactionRepository.saveAndFlush(
						Transaction.builder()
								.tipo(TransactionType.DESPESA)
								.valor(new BigDecimal("50.00"))
								.data(LocalDate.of(2026, 1, 1))
								.build());

		TransactionRequest update =
				new TransactionRequest("receita", new BigDecimal("999.99"), "Atualizada", "servicos", "2026-01-15", null);

		mockMvc
				.perform(
						put("/api/transactions/{id}", saved.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("receita"))
				.andExpect(jsonPath("$.valor").value(999.99))
				.andExpect(jsonPath("$.descricao").value("Atualizada"));
	}

	/**
	 * Deletes a transaction.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletesATransaction() throws Exception {
		Transaction saved =
				transactionRepository.saveAndFlush(
						Transaction.builder()
								.tipo(TransactionType.DESPESA)
								.valor(new BigDecimal("50.00"))
								.data(LocalDate.of(2026, 1, 1))
								.build());

		mockMvc
				.perform(
						delete("/api/transactions/{id}", saved.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isNoContent());

		assertThat(transactionRepository.existsById(saved.getId())).isFalse();
	}

	/**
	 * Acceptance criterion 5: {@code GET /api/transactions?from=&to=} excludes transactions dated
	 * outside the requested interval (3 dates: before, inside, after).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void filtersTheListingByDateRange() throws Exception {
		transactionRepository.saveAndFlush(
				Transaction.builder()
						.tipo(TransactionType.DESPESA)
						.valor(new BigDecimal("10.00"))
						.data(LocalDate.of(2026, 1, 1))
						.build());
		Transaction insideRange =
				transactionRepository.saveAndFlush(
						Transaction.builder()
								.tipo(TransactionType.DESPESA)
								.valor(new BigDecimal("20.00"))
								.data(LocalDate.of(2026, 2, 15))
								.build());
		transactionRepository.saveAndFlush(
				Transaction.builder()
						.tipo(TransactionType.DESPESA)
						.valor(new BigDecimal("30.00"))
						.data(LocalDate.of(2026, 3, 31))
						.build());

		mockMvc
				.perform(
						get("/api/transactions?from=2026-02-01&to=2026-02-28")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(insideRange.getId().toString()));
	}

	/**
	 * {@code GET /api/transactions} filters by {@code ?tipo=}, using the lower-case database/JSON
	 * value (not the upper-snake-case Java constant name), exercising {@link
	 * TransactionTypeQueryConverter}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void filtersTheListingByTipo() throws Exception {
		Transaction despesa =
				transactionRepository.saveAndFlush(
						Transaction.builder()
								.tipo(TransactionType.DESPESA)
								.valor(new BigDecimal("10.00"))
								.data(LocalDate.of(2026, 1, 1))
								.build());
		transactionRepository.saveAndFlush(
				Transaction.builder()
						.tipo(TransactionType.RECEITA)
						.valor(new BigDecimal("20.00"))
						.data(LocalDate.of(2026, 1, 2))
						.build());

		mockMvc
				.perform(
						get("/api/transactions?tipo=despesa")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(despesa.getId().toString()));
	}

	/**
	 * Acceptance criterion 6: every {@code /api/transactions} endpoint responds 401 without a token.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void listingTransactionsWithoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
	}

	/**
	 * Acceptance criterion 8: {@code POST /api/transactions} with {@code data =
	 * "2026-03-31T23:30:00Z"} (a full ISO instant, the format {@code dcbo/src/App.js:231}'s fallback
	 * sends) stores {@code data = 2026-04-01} — the calendar date in {@code Europe/Lisbon}, which is
	 * already in daylight saving time (UTC+1) by that date, one day ahead of the UTC instant.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void resolvesAFullIsoInstantToTheLisbonCalendarDate() throws Exception {
		TransactionRequest valid = aRequest();
		TransactionRequest withInstant =
				new TransactionRequest(
						valid.tipo(), valid.valor(), valid.descricao(), valid.categoria(), "2026-03-31T23:30:00Z", null);

		String location =
				mockMvc
						.perform(
								post("/api/transactions")
										.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(withInstant)))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getHeader("Location");

		mockMvc
				.perform(get(location).with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value("2026-04-01"));
	}

	/**
	 * Acceptance criterion 8 counterpart: {@code data = "2026-03-31"} (a plain calendar date) is
	 * stored as-is.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void resolvesAPlainCalendarDateAsIs() throws Exception {
		TransactionRequest valid = aRequest();
		TransactionRequest withPlainDate =
				new TransactionRequest(
						valid.tipo(), valid.valor(), valid.descricao(), valid.categoria(), "2026-03-31", null);

		mockMvc
				.perform(
						post("/api/transactions")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(withPlainDate)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data").value("2026-03-31"));
	}

	/**
	 * A malformed {@code data} (neither format) responds 400.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void rejectsAMalformedData() throws Exception {
		TransactionRequest valid = aRequest();
		TransactionRequest withMalformedDate =
				new TransactionRequest(
						valid.tipo(), valid.valor(), valid.descricao(), valid.categoria(), "not-a-date", null);

		mockMvc
				.perform(
						post("/api/transactions")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(withMalformedDate)))
				.andExpect(status().isBadRequest());
	}

	/**
	 * Acceptance criterion 3: selling a car creates a {@code venda} transaction associated with it;
	 * reverting the sale removes it.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void sellingACarCreatesAVendaTransactionAndRevertingRemovesIt() throws Exception {
		Client client =
				clientRepository.saveAndFlush(Client.builder().name("Cliente Teste").phone("912345678").build());
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		SellCarRequest sellRequest = new SellCarRequest(new BigDecimal("21000.00"), client.getId());

		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(sellRequest)))
				.andExpect(status().isOk());

		assertThat(transactionRepository.findByCarId(car.getId())).hasSize(1);
		Transaction saleTransaction = transactionRepository.findByCarId(car.getId()).get(0);
		assertThat(saleTransaction.getTipo()).isEqualTo(TransactionType.VENDA);
		assertThat(saleTransaction.getValor()).isEqualByComparingTo(new BigDecimal("21000.00"));
		assertThat(saleTransaction.isSystemGenerated()).isTrue();

		mockMvc
				.perform(
						post("/api/cars/{id}/revert-sale", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk());

		assertThat(transactionRepository.findByCarId(car.getId())).isEmpty();
	}

	/**
	 * Requirement 3 refinement: reverting a sale never removes a manual, non-system-generated
	 * transaction that happens to reference the same car.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void revertingASaleNeverRemovesAManualTransactionForTheSameCar() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		Transaction manualTransaction =
				transactionRepository.saveAndFlush(
						Transaction.builder()
								.tipo(TransactionType.VENDA)
								.valor(new BigDecimal("500.00"))
								.data(LocalDate.of(2026, 1, 1))
								.car(car)
								.systemGenerated(false)
								.build());
		SellCarRequest sellRequest = new SellCarRequest(new BigDecimal("21000.00"), null);

		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(sellRequest)))
				.andExpect(status().isOk());

		mockMvc
				.perform(
						post("/api/cars/{id}/revert-sale", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk());

		assertThat(transactionRepository.findByCarId(car.getId()))
				.extracting(Transaction::getId)
				.containsExactly(manualTransaction.getId());
	}

	/**
	 * Acceptance criterion 4: deleting a car with 2 transactions removes both — proved by id, not by
	 * {@link TransactionRepository#findByCarId}, because {@code transactions.car_id} is {@code ON
	 * DELETE SET NULL} (TASK-005, {@code V1__init.sql:92}): a car deletion that merely detached the
	 * transactions (leaving them with {@code car_id = NULL}, the {@code deleteTransactions=false}
	 * branch of {@code DELETE /api/cars/{id}} added by {@code backlog/reviews/TASK-011-r1.md}
	 * IMPORTANTE 2) would still make {@code findByCarId} return empty, so that query alone cannot
	 * distinguish "deleted" from "merely disassociated". Deliberately calls the endpoint without
	 * {@code ?deleteTransactions=}, proving {@code true} is still the default (requirement 4 never
	 * describes opting out).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletingACarRemovesItsTransactions() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		Transaction first =
				transactionRepository.saveAndFlush(
						Transaction.builder()
								.tipo(TransactionType.COMPRA)
								.valor(new BigDecimal("18000.00"))
								.data(LocalDate.of(2026, 1, 1))
								.car(car)
								.build());
		Transaction second =
				transactionRepository.saveAndFlush(
						Transaction.builder()
								.tipo(TransactionType.DESPESA)
								.valor(new BigDecimal("300.00"))
								.data(LocalDate.of(2026, 1, 5))
								.car(car)
								.build());

		assertThat(transactionRepository.findByCarId(car.getId())).hasSize(2);

		mockMvc
				.perform(
						delete("/api/cars/{id}", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isNoContent());

		assertThat(transactionRepository.findAllById(List.of(first.getId(), second.getId()))).isEmpty();
	}

	/**
	 * Requirement 4 refinement ({@code backlog/reviews/TASK-011-r1.md} IMPORTANTE 1): {@code
	 * DELETE /api/cars/{id}?deleteTransactions=false} keeps a car's transactions, mirroring the
	 * unchecked state of {@code dcbo}'s "Eliminar também as transações financeiras" checkbox ({@code
	 * dcbo/src/pages/cars.js:662-679}) — the {@code ON DELETE SET NULL} foreign key detaches them
	 * ({@code carroId} becomes {@code null}) but the rows, and their contribution to {@code saldo},
	 * survive.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletingACarWithDeleteTransactionsFalseKeepsItsTransactions() throws Exception {
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		Transaction kept =
				transactionRepository.saveAndFlush(
						Transaction.builder()
								.tipo(TransactionType.COMPRA)
								.valor(new BigDecimal("18000.00"))
								.data(LocalDate.of(2026, 1, 1))
								.car(car)
								.build());

		mockMvc
				.perform(
						delete("/api/cars/{id}", car.getId())
								.queryParam("deleteTransactions", "false")
								.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isNoContent());

		Transaction reloaded = transactionRepository.findById(kept.getId()).orElseThrow();
		assertThat(reloaded.getCar()).isNull();
	}

	/**
	 * Requirement 4 refinement ({@code backlog/reviews/TASK-011-r1.md} IMPORTANTE 1): deleting a
	 * sold car with {@code deleteTransactions=true} (the default) also decrements the buying
	 * client's {@code purchasesCount}, mirroring {@code dcbo/src/App.js:304-306}'s {@code
	 * decrementClientPurchases}, called there under the same condition.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void deletingASoldCarDecrementsTheBuyingClientsPurchasesCount() throws Exception {
		Client client =
				clientRepository.saveAndFlush(Client.builder().name("Cliente Teste").phone("912345678").build());
		Car car = carRepository.saveAndFlush(aPersistedCar().build());
		SellCarRequest sellRequest = new SellCarRequest(new BigDecimal("21000.00"), client.getId());
		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(sellRequest)))
				.andExpect(status().isOk());
		assertThat(clientRepository.findById(client.getId()).orElseThrow().getPurchasesCount()).isEqualTo(1);

		mockMvc
				.perform(
						delete("/api/cars/{id}", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isNoContent());

		assertThat(clientRepository.findById(client.getId()).orElseThrow().getPurchasesCount()).isEqualTo(0);
	}

	/**
	 * IMPORTANTE 3 ({@code backlog/reviews/TASK-011-r1.md}): selling a consignment car creates the
	 * automatic {@code venda} transaction with the partner's <em>commission</em> as {@code valor},
	 * never the full sale price ({@code CarService.java:223-229}) — it is real money in {@code
	 * saldo}, and the gap between the two is 20× in a typical sale, yet no test covered this branch
	 * before this fix ({@code grep -rn "comissao_venda" src/test} was empty). Also proves the
	 * commission, not the sale price, is what {@code GET /api/finances/summary} counts, and that
	 * {@code revert-sale} removes it like any other system-generated sale transaction.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void sellingAConsignmentCarCreatesAVendaTransactionForTheCommissionNotTheSalePrice() throws Exception {
		Partner partner = partnerRepository.saveAndFlush(Partner.builder().name("Parceiro Teste").build());
		Car car =
				carRepository.saveAndFlush(
						aPersistedCar()
								.consignacao(true)
								.partner(partner)
								.commissionValue(new BigDecimal("1000.00"))
								.build());
		SellCarRequest sellRequest = new SellCarRequest(new BigDecimal("21000.00"), null);

		mockMvc
				.perform(
						post("/api/cars/{id}/sell", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE)))
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(sellRequest)))
				.andExpect(status().isOk());

		assertThat(transactionRepository.findByCarId(car.getId())).hasSize(1);
		Transaction commissionTransaction = transactionRepository.findByCarId(car.getId()).get(0);
		assertThat(commissionTransaction.getTipo()).isEqualTo(TransactionType.VENDA);
		assertThat(commissionTransaction.getValor()).isEqualByComparingTo(new BigDecimal("1000.00"));
		assertThat(commissionTransaction.getCategoria()).isEqualTo("comissao_venda");
		assertThat(commissionTransaction.getPartner()).isNotNull();
		assertThat(commissionTransaction.getPartner().getId()).isEqualTo(partner.getId());
		assertThat(commissionTransaction.isSystemGenerated()).isTrue();

		mockMvc
				.perform(
						get("/api/finances/summary")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalReceitas").value(1000.00));

		mockMvc
				.perform(
						post("/api/cars/{id}/revert-sale", car.getId())
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk());

		assertThat(transactionRepository.findByCarId(car.getId())).isEmpty();
	}
}

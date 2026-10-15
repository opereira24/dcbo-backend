package pt.diamondcars.dcbobackend.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import pt.diamondcars.dcbobackend.domain.transaction.Transaction;
import pt.diamondcars.dcbobackend.domain.transaction.TransactionRepository;
import pt.diamondcars.dcbobackend.domain.transaction.TransactionType;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;

/**
 * End-to-end tests of {@link FinanceController}/{@link
 * pt.diamondcars.dcbobackend.service.TransactionService#summary} through the real servlet filter
 * chain (TASK-011), using {@link MockMvc} against a real PostgreSQL container ({@link
 * AbstractPostgresIntegrationTest}), mirroring the idiom {@code CarControllerTest} (TASK-008)
 * established.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class FinanceControllerTest extends AbstractPostgresIntegrationTest {

	private static final String USER_ROLE = "ROLE_USER";

	@Autowired private MockMvc mockMvc;
	@Autowired private TransactionRepository transactionRepository;

	/**
	 * Clears every row this class writes before each test, for the same reason {@code
	 * CarControllerTest#cleanDatabase()} does (shared, JVM-wide singleton container, no per-test
	 * rollback).
	 */
	@BeforeEach
	void cleanDatabase() {
		transactionRepository.deleteAll();
	}

	/**
	 * Acceptance criterion 2: given a 1000,00 receita and a 250,50 despesa in the requested interval,
	 * {@code GET /api/finances/summary} returns {@code saldo} = 749,50 exactly.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void computesTheExactSaldoForKnownReceitasAndDespesas() throws Exception {
		transactionRepository.saveAndFlush(
				Transaction.builder()
						.tipo(TransactionType.RECEITA)
						.valor(new BigDecimal("1000.00"))
						.data(LocalDate.of(2026, 2, 10))
						.build());
		transactionRepository.saveAndFlush(
				Transaction.builder()
						.tipo(TransactionType.DESPESA)
						.valor(new BigDecimal("250.50"))
						.data(LocalDate.of(2026, 2, 15))
						.build());
		// Outside the requested interval: must not affect the summary.
		transactionRepository.saveAndFlush(
				Transaction.builder()
						.tipo(TransactionType.RECEITA)
						.valor(new BigDecimal("5000.00"))
						.data(LocalDate.of(2026, 3, 1))
						.build());

		mockMvc
				.perform(
						get("/api/finances/summary?from=2026-02-01&to=2026-02-28")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalReceitas").value(1000.00))
				.andExpect(jsonPath("$.totalDespesas").value(250.50))
				.andExpect(jsonPath("$.saldo").value(749.50));
	}

	/**
	 * {@code venda}/{@code compra} transactions are grouped into {@code totalReceitas}/{@code
	 * totalDespesas} respectively, mirroring {@code dcbo/src/App.js:196-200}'s {@code
	 * calculateBalance}.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void groupsVendaWithReceitaAndCompraWithDespesa() throws Exception {
		transactionRepository.saveAndFlush(
				Transaction.builder()
						.tipo(TransactionType.VENDA)
						.valor(new BigDecimal("15000.00"))
						.data(LocalDate.of(2026, 4, 1))
						.build());
		transactionRepository.saveAndFlush(
				Transaction.builder()
						.tipo(TransactionType.COMPRA)
						.valor(new BigDecimal("10000.00"))
						.data(LocalDate.of(2026, 4, 2))
						.build());

		mockMvc
				.perform(
						get("/api/finances/summary?from=2026-04-01&to=2026-04-30")
								.with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalReceitas").value(15000.00))
				.andExpect(jsonPath("$.totalDespesas").value(10000.00))
				.andExpect(jsonPath("$.saldo").value(5000.00));
	}

	/**
	 * {@code GET /api/finances/summary} without {@code from}/{@code to} covers every transaction.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void summaryWithoutABoundedRangeCoversEveryTransaction() throws Exception {
		transactionRepository.saveAndFlush(
				Transaction.builder()
						.tipo(TransactionType.RECEITA)
						.valor(new BigDecimal("100.00"))
						.data(LocalDate.of(2020, 1, 1))
						.build());
		transactionRepository.saveAndFlush(
				Transaction.builder()
						.tipo(TransactionType.RECEITA)
						.valor(new BigDecimal("200.00"))
						.data(LocalDate.of(2030, 1, 1))
						.build());

		mockMvc
				.perform(get("/api/finances/summary").with(jwt().authorities(new SimpleGrantedAuthority(USER_ROLE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalReceitas").value(300.00));
	}

	/**
	 * {@code GET /api/finances/summary} responds 401 without a token.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void summaryWithoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/finances/summary")).andExpect(status().isUnauthorized());
	}
}

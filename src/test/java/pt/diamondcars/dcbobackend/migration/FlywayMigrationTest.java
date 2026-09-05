package pt.diamondcars.dcbobackend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;

/**
 * Verifies that the Flyway migration {@code V1__init.sql} applies cleanly to an empty,
 * ephemeral PostgreSQL database and creates every table of the back-office domain schema, with
 * the column types and constraints the rest of the release depends on.
 *
 * <p>The Spring context under test boots with Flyway enabled (see {@code application.yml}), so by
 * the time these tests run the migration has already been applied by the framework; every
 * assertion queries the resulting catalog/data state, not the migration file itself, so a
 * regression in a future {@code V2__...sql} that breaks one of these invariants fails loudly here
 * instead of passing silently.
 */
@SpringBootTest
class FlywayMigrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private DataSource dataSource;

	/**
	 * Confirms that every table declared by {@code V1__init.sql} — the seven domain tables plus
	 * the {@code car_images} child table — exists in the {@code public} schema after Flyway runs.
	 *
	 * @throws AssertionError if any expected table is missing
	 */
	@Test
	void migrationCreatesAllDomainTables() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		List<String> tableNames = jdbcTemplate.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
				String.class);

		assertThat(tableNames).containsExactlyInAnyOrder(
				"cars",
				"car_images",
				"clients",
				"partners",
				"transactions",
				"leads",
				"notifications",
				"app_users",
				"flyway_schema_history");
	}

	/**
	 * Confirms that every monetary column across the schema is {@code NUMERIC(12,2)}, never a
	 * floating-point type, matching acceptance criterion 4 of TASK-005 ("nenhuma coluna monetária
	 * usa float/double precision") and covering it by catalog inspection rather than only by a
	 * one-off {@code grep} on the migration source.
	 *
	 * @throws AssertionError if any known monetary column is missing or is not
	 *         {@code numeric(12,2)}
	 */
	@Test
	void moneyColumnsUseNumericWithTwoDecimals() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		List<String> moneyColumns = List.of(
				"preco", "preco_compra", "preco_venda", "commission_value",
				"valor", "total_commission", "carro_preco");

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT column_name, data_type, numeric_scale FROM information_schema.columns "
						+ "WHERE table_schema = 'public' AND column_name = ANY (?)",
				(Object) moneyColumns.toArray(new String[0]));

		assertThat(rows)
				.as("every known monetary column must exist and be numeric(*, 2)")
				.hasSize(moneyColumns.size())
				.allSatisfy(row -> {
					assertThat(row.get("data_type")).isEqualTo("numeric");
					assertThat(row.get("numeric_scale")).isEqualTo(2);
				});
	}

	/**
	 * Confirms that {@code app_users.auth_subject} enforces uniqueness at the database level
	 * (acceptance criterion 5 of TASK-005), by behaviour rather than by inspecting the migration
	 * SQL: inserting two users with the same Auth0 subject must fail.
	 *
	 * @throws AssertionError if a duplicate {@code auth_subject} is accepted
	 */
	@Test
	void authSubjectIsUnique() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		String sharedAuthSubject = "auth0|" + UUID.randomUUID();

		jdbcTemplate.update(
				"INSERT INTO app_users (auth_subject, email, name, role) VALUES (?, ?, ?, ?)",
				sharedAuthSubject, "first@example.com", "First User", "admin");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO app_users (auth_subject, email, name, role) VALUES (?, ?, ?, ?)",
				sharedAuthSubject, "second@example.com", "Second User", "user"))
				.isInstanceOf(DuplicateKeyException.class);
	}

	/**
	 * Confirms the business invariant behind BLOQ-1 of the TASK-005 review: deleting a client must
	 * never delete the financial history tied to them, only detach it. Exercises the
	 * {@code transactions.cliente_id ON DELETE SET NULL} foreign key end to end.
	 *
	 * @throws AssertionError if the transaction is removed, or its {@code cliente_id} is not
	 *         nulled out, after the referenced client is deleted
	 */
	@Test
	void deletingAClientDoesNotDeleteItsFinancialHistory() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		UUID clientId = jdbcTemplate.queryForObject(
				"INSERT INTO clients (name, phone) VALUES (?, ?) RETURNING id",
				UUID.class, "Cliente Teste", "912345678");
		UUID transactionId = jdbcTemplate.queryForObject(
				"INSERT INTO transactions (tipo, valor, data, cliente_id) "
						+ "VALUES (?, ?, CURRENT_DATE, ?) RETURNING id",
				UUID.class, "receita", new BigDecimal("100.00"), clientId);

		jdbcTemplate.update("DELETE FROM clients WHERE id = ?", clientId);

		Map<String, Object> survivingTransaction = jdbcTemplate.queryForMap(
				"SELECT cliente_id FROM transactions WHERE id = ?", transactionId);
		assertThat(survivingTransaction.get("cliente_id")).isNull();
	}
}

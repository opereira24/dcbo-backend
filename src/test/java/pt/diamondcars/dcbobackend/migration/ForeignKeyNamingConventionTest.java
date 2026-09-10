package pt.diamondcars.dcbobackend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;

/**
 * Locks in ADR-001 (Camada 1, {@code backlog/CONVENTIONS.md}) for the future: every foreign-key
 * column of the {@code public} schema must have an English name, and the three columns renamed by
 * {@code V2__rename_fk_columns_to_english.sql} ({@code cars.client_id},
 * {@code transactions.client_id}, {@code leads.car_id}) must exist with their original
 * {@code ON DELETE SET NULL} behaviour preserved.
 *
 * <p>This test does not police business attributes (ADR-001, Camada 2, e.g. {@code marca},
 * {@code preco}, {@code carro_marca}) — those are expected to stay in Portuguese, mirroring the
 * {@code dc}/{@code dcbo} frontends, and are outside the scope of this convention.
 */
@SpringBootTest
class ForeignKeyNamingConventionTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private DataSource dataSource;

	/**
	 * Confirms that none of the Portuguese FK names known to have existed (or that could plausibly
	 * be reintroduced by mistake) are present anywhere in the {@code public} schema, so that a
	 * future {@code V3} migration that reintroduces one of them turns this test red instead of
	 * silently passing.
	 *
	 * @throws AssertionError if any blacklisted, Portuguese-named FK column exists
	 */
	@Test
	void noForeignKeyColumnUsesAPortugueseName() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		List<String> portugueseFkBlacklist = List.of(
				"cliente_id", "carro_id", "parceiro_id", "utilizador_id", "transacao_id", "imagem_id");

		List<Map<String, Object>> matches = jdbcTemplate.queryForList(
				"SELECT table_name, column_name FROM information_schema.columns "
						+ "WHERE table_schema = 'public' AND column_name = ANY (?)",
				(Object) portugueseFkBlacklist.toArray(new String[0]));

		assertThat(matches)
				.as("no FK column may use a Portuguese name (ADR-001, Camada 1): %s", matches)
				.isEmpty();
	}

	/**
	 * Confirms the three columns renamed by {@code V2__rename_fk_columns_to_english.sql} exist with
	 * their new, English names, and that the old, Portuguese names are gone.
	 *
	 * @throws AssertionError if any renamed column is missing, or any old column still exists
	 */
	@Test
	void renamedForeignKeyColumnsExistAndOldNamesAreGone() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		List<String> renamedColumns = jdbcTemplate.queryForList(
				"SELECT table_name || '.' || column_name FROM information_schema.columns "
						+ "WHERE table_schema = 'public' AND ("
						+ "(table_name = 'cars' AND column_name = 'client_id') OR "
						+ "(table_name = 'transactions' AND column_name = 'client_id') OR "
						+ "(table_name = 'leads' AND column_name = 'car_id'))",
				String.class);

		assertThat(renamedColumns)
				.containsExactlyInAnyOrder("cars.client_id", "transactions.client_id", "leads.car_id");

		List<String> oldColumns = jdbcTemplate.queryForList(
				"SELECT table_name || '.' || column_name FROM information_schema.columns "
						+ "WHERE table_schema = 'public' AND column_name IN ('cliente_id', 'carro_id')",
				String.class);

		assertThat(oldColumns).isEmpty();
	}

	/**
	 * Confirms {@code RENAME COLUMN} preserved the {@code ON DELETE SET NULL} behaviour of the three
	 * renamed foreign keys, covering requirement 2 of TASK-037: a rename must never silently drop or
	 * change a constraint.
	 *
	 * @throws AssertionError if any of the three renamed FK columns is missing its foreign key, or
	 *         the {@code delete_rule} is not {@code SET NULL}
	 */
	@Test
	void renamedForeignKeysStillSetNullOnDelete() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList(
				"SELECT tc.table_name, kcu.column_name, rc.delete_rule "
						+ "FROM information_schema.table_constraints tc "
						+ "JOIN information_schema.key_column_usage kcu "
						+ "  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema "
						+ "JOIN information_schema.referential_constraints rc "
						+ "  ON tc.constraint_name = rc.constraint_name AND tc.table_schema = rc.constraint_schema "
						+ "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public' "
						+ "  AND ((tc.table_name = 'cars' AND kcu.column_name = 'client_id') "
						+ "    OR (tc.table_name = 'transactions' AND kcu.column_name = 'client_id') "
						+ "    OR (tc.table_name = 'leads' AND kcu.column_name = 'car_id'))");

		assertThat(foreignKeys)
				.as("the three renamed FK columns must keep their ON DELETE SET NULL constraint: %s",
						foreignKeys)
				.hasSize(3)
				.allSatisfy(row -> assertThat(row.get("delete_rule")).isEqualTo("SET NULL"));
	}
}

package pt.diamondcars.dcbobackend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;

/**
 * Verifies that the Flyway migration {@code V1__init.sql} applies cleanly to an empty,
 * ephemeral PostgreSQL database and creates every table of the back-office domain schema.
 *
 * <p>The Spring context under test boots with Flyway enabled (see {@code application.yml}), so by
 * the time this test runs the migration has already been applied by the framework; the test only
 * asserts on the resulting {@code information_schema.tables} state.
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
}

package pt.diamondcars.dcbobackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;

/**
 * Smoke test that verifies the Spring application context starts up successfully against a real,
 * ephemeral PostgreSQL database provided by {@link AbstractPostgresIntegrationTest}.
 */
@SpringBootTest
class DcboBackendApplicationTests extends AbstractPostgresIntegrationTest {

	/**
	 * Verifies that the full Spring application context loads without errors, with the datasource
	 * wired to the Testcontainers-managed PostgreSQL instance instead of the placeholder configured
	 * in {@code application.yml}.
	 */
	@Test
	void contextLoads() {
	}

}

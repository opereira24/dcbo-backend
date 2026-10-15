package pt.diamondcars.dcbobackend.web.internal;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import pt.diamondcars.dcbobackend.domain.lead.LeadRepository;
import pt.diamondcars.dcbobackend.domain.notification.NotificationRepository;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;
import pt.diamondcars.dcbobackend.web.dto.InternalLeadRequest;

/**
 * Regression test for BLOQUEADOR 1 ({@code backlog/reviews/TASK-010-r1.md}): {@code
 * POST /%69nternal/leads} — a path-encoded variant of {@code /internal/leads}, where {@code %69}
 * decodes to {@code i} — must still be rejected without the shared token, exactly like the literal
 * path, and must never create a lead or notification.
 *
 * <p>Deliberately <em>not</em> a {@link org.springframework.test.web.servlet.MockMvc} test: {@code
 * MockMvc} decodes/normalizes the request path before the servlet filter chain ever runs, which is
 * exactly why the original bug — {@link pt.diamondcars.dcbobackend.config.InternalTokenFilter}
 * comparing the shared-token requirement against the raw, undecoded {@code getRequestURI()}, while
 * the old {@code permitAll()} rule and the {@code DispatcherServlet} compared against the decoded
 * path — was invisible to {@code MockMvc} and only reproducible against a real servlet container.
 * This test starts a real embedded Tomcat ({@code webEnvironment = RANDOM_PORT}) and sends the
 * request with {@link HttpClient}, which — like {@code catalog-backend} or any other real HTTP
 * client — preserves the {@code %69} escape on the wire instead of decoding it locally, the same
 * way the reviewer's probe (BLOQUEADOR 1, sonda p02) reproduced the bypass.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "catalog.sync.internal-token=" + InternalTokenFilterEncodedPathTest.TEST_TOKEN)
class InternalTokenFilterEncodedPathTest extends AbstractPostgresIntegrationTest {

	/**
	 * A token that satisfies {@link pt.diamondcars.dcbobackend.config.InternalTokenFilter}'s
	 * minimum-length/not-a-placeholder rule, used only by this test class — never a real secret.
	 */
	static final String TEST_TOKEN = "test-only-internal-token-9876543210";

	@LocalServerPort private int port;

	@Autowired private ObjectMapper objectMapper;
	@Autowired private LeadRepository leadRepository;
	@Autowired private NotificationRepository notificationRepository;

	/**
	 * Clears every row this class writes before each test, for the same reason {@code
	 * InternalLeadControllerTest#cleanDatabase()} does (shared, JVM-wide singleton container, no
	 * per-test rollback).
	 */
	@BeforeEach
	void cleanDatabase() {
		notificationRepository.deleteAll();
		leadRepository.deleteAll();
	}

	/**
	 * Reproduces the exact probe from {@code backlog/reviews/TASK-010-r1.md} (BLOQUEADOR 1, sonda
	 * p02): {@code POST /%69nternal/leads} with no {@code X-Internal-Token} header must respond 401
	 * and must not create a lead or notification — where, before the fix, it responded 201 and
	 * created both.
	 *
	 * @throws Exception propagated from {@link HttpClient#send}
	 */
	@Test
	void rejectsAnEncodedInternalPathWithoutTheTokenHeader() throws Exception {
		String body =
				objectMapper.writeValueAsString(
						new InternalLeadRequest(
								"Maria Visitante", "maria@example.com", "912345678", "Quero saber mais", null, "Audi",
								"A4", "website"));

		HttpRequest request =
				HttpRequest.newBuilder()
						.uri(URI.create("http://localhost:" + port + "/%69nternal/leads"))
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(body))
						.build();

		HttpResponse<String> response =
				HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(leadRepository.count()).isZero();
		assertThat(notificationRepository.count()).isZero();
	}
}

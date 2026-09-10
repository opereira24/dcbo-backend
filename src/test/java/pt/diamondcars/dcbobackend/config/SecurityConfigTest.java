package pt.diamondcars.dcbobackend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import pt.diamondcars.dcbobackend.config.support.TestJwtSupport;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;

/**
 * End-to-end test of {@link SecurityConfig} through the real servlet filter chain (TASK-007
 * requirement 7 / acceptance criteria), using {@link MockMvc} instead of a running server. No test
 * here ever reaches the real Auth0 issuer: the {@link JwtDecoder} used to validate a token that
 * must be actually decoded (the wrong-audience case) is replaced by {@link
 * JwtDecoderTestConfig#jwtDecoder()}, backed by a locally generated HMAC key (see {@link
 * TestJwtSupport}); the "valid token" case instead uses {@link
 * org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#jwt()},
 * which injects an already-authenticated principal without ever calling a decoder at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(SecurityConfigTest.JwtDecoderTestConfig.class)
class SecurityConfigTest extends AbstractPostgresIntegrationTest {

	private static final String PROTECTED_PROBE_PATH = "/api/security-probe";

	@Autowired private MockMvc mockMvc;

	/**
	 * Acceptance criterion: {@code GET /actuator/health} without a token responds 200.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void healthEndpointIsPublicWithoutAToken() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	/**
	 * Acceptance criterion: any route other than {@code /actuator/health}/{@code /actuator/info}
	 * responds 401 when called without an {@code Authorization} header.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void anyOtherRouteRequiresAuthenticationWhenNoTokenIsSent() throws Exception {
		mockMvc.perform(get(PROTECTED_PROBE_PATH)).andExpect(status().isUnauthorized());
	}

	/**
	 * Acceptance criterion: a structurally valid JWT issued for a different audience is rejected
	 * with 401. Uses a real (offline) decode + validate cycle via {@link JwtDecoderTestConfig}, not
	 * a mocked {@link org.springframework.security.core.Authentication}, since it is exactly the
	 * validator logic that is under test here.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void aTokenIssuedForAnotherAudienceIsRejected() throws Exception {
		String tokenForAnotherAudience =
				TestJwtSupport.signedToken("auth0|someone", List.of("https://another-audience.example/"));

		mockMvc
				.perform(
						get(PROTECTED_PROBE_PATH)
								.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenForAnotherAudience))
				.andExpect(status().isUnauthorized());
	}

	/**
	 * Acceptance criterion: an authenticated request carrying the expected authority is let through
	 * the security chain (it may still 404 past that point, since no controller is mapped to {@code
	 * PROTECTED_PROBE_PATH} — TASK-008 to TASK-012 add real endpoints later — but it must never be
	 * rejected as 401/403).
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void aValidJwtWithTheExpectedAuthorityIsNotRejected() throws Exception {
		mockMvc
				.perform(
						get(PROTECTED_PROBE_PATH)
								.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(
						result ->
								assertThat(result.getResponse().getStatus())
										.isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));
	}

	/**
	 * Swaps the production {@link JwtDecoder} (auto-configured by Spring Boot against the
	 * placeholder Auth0 issuer) for one backed by a local HMAC key, so {@link
	 * #aTokenIssuedForAnotherAudienceIsRejected()} can drive a real decode-and-validate cycle
	 * without any network access.
	 */
	@TestConfiguration
	static class JwtDecoderTestConfig {

		/**
		 * @return a {@link JwtDecoder} that only accepts {@link TestJwtSupport#VALID_AUDIENCE}
		 */
		@Bean
		JwtDecoder jwtDecoder() {
			return TestJwtSupport.decoderAcceptingAudience(TestJwtSupport.VALID_AUDIENCE);
		}
	}
}

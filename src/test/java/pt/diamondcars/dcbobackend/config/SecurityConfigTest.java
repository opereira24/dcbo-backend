package pt.diamondcars.dcbobackend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.diamondcars.dcbobackend.config.support.TestJwtSupport;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;

/**
 * End-to-end test of {@link SecurityConfig} through the real servlet filter chain (TASK-007
 * requirement 7 / acceptance criteria), using {@link MockMvc} instead of a running server. No test
 * here ever reaches the real Auth0 issuer: the {@link JwtDecoder} used to validate a token that
 * must be actually decoded (the wrong-audience case, and the roles-claim case below) is replaced by
 * {@link JwtDecoderTestConfig#jwtDecoder()}, backed by a locally generated HMAC key (see {@link
 * TestJwtSupport}); only {@link #aValidJwtWithTheExpectedAuthorityIsNotRejected()} still uses
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#jwt()},
 * which injects an already-authenticated principal without ever calling a decoder or converter — it
 * is kept as a narrower, additional check, not the only proof that roles are wired end-to-end (see
 * {@link #aValidJwtWithARoleClaimIsConvertedIntoTheMatchingRoleAuthority()}, added after
 * {@code backlog/reviews/TASK-007-r1.md}, BLOQUEADOR 1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(SecurityConfigTest.JwtDecoderTestConfig.class)
class SecurityConfigTest extends AbstractPostgresIntegrationTest {

	private static final String PROTECTED_PROBE_PATH = "/api/security-probe";

	@Autowired private MockMvc mockMvc;

	@Autowired private OAuth2ResourceServerProperties resourceServerProperties;

	@Value("${app.auth0.roles-claim}")
	private String rolesClaim;

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
	 * the security chain, and reaches the mapped controller with that authority intact. Uses the
	 * {@code jwt()} post-processor, which injects the authority directly instead of exercising the
	 * decoder/converter — see {@link #aValidJwtWithARoleClaimIsConvertedIntoTheMatchingRoleAuthority()}
	 * for the test that proves the conversion itself happens through the real chain.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void aValidJwtWithTheExpectedAuthorityIsNotRejected() throws Exception {
		mockMvc
				.perform(
						get(PROTECTED_PROBE_PATH)
								.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("ROLE_ADMIN")));
	}

	/**
	 * Regression test for {@code backlog/reviews/TASK-007-r1.md}, BLOQUEADOR 1: proves that a real
	 * Auth0 role claim, carried by a JWT that goes through an actual decode (via {@link
	 * JwtDecoderTestConfig}) and then through the production {@link
	 * SecurityConfig#jwtAuthenticationConverter} bean — i.e. the real {@link Auth0RolesConverter} —
	 * ends up as a {@code ROLE_*} {@link GrantedAuthority} in the {@link
	 * org.springframework.security.core.context.SecurityContext} of the request, and is visible to
	 * the mapped controller ({@link SecurityProbeController}).
	 *
	 * <p>Confirmed red before being fixed: severing {@code Auth0RolesConverter} from the {@code
	 * JwtAuthenticationConverter} bean (removing {@code
	 * converter.setJwtGrantedAuthoritiesConverter(auth0RolesConverter)} in {@code
	 * SecurityConfig.jwtAuthenticationConverter}, which falls back to Spring Security's default
	 * {@code JwtGrantedAuthoritiesConverter} that only produces {@code SCOPE_*} authorities from the
	 * {@code scope}/{@code scp} claim) makes this test fail (empty authorities), while every other
	 * test in the module stays green — reproduced and reverted by the developer before closing the
	 * review. Note this is a more faithful mutation than removing the DSL call {@code
	 * jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)} in {@code securityFilterChain}: that
	 * one alone is a no-op here, because Spring Security's {@code JwtConfigurer} falls back to
	 * looking up a {@code JwtAuthenticationConverter} bean from the context when the DSL does not set
	 * one explicitly, and this module happens to declare exactly one such bean.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void aValidJwtWithARoleClaimIsConvertedIntoTheMatchingRoleAuthority() throws Exception {
		String tokenWithAdminRole =
				TestJwtSupport.signedTokenWithClaim(
						"auth0|admin-user",
						List.of(TestJwtSupport.VALID_AUDIENCE),
						rolesClaim,
						List.of("admin"));

		mockMvc
				.perform(
						get(PROTECTED_PROBE_PATH)
								.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAdminRole))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("ROLE_ADMIN")));
	}

	/**
	 * Regression test for {@code backlog/reviews/TASK-007-r1.md}, IMPORTANTE 1: proves that the
	 * {@code spring.security.oauth2.resourceserver.jwt.audiences} property that production actually
	 * reads (via Spring Boot's auto-configured {@link OAuth2ResourceServerProperties}, the same bean
	 * {@code JwtDecoderConfiguration.getValidator()} consumes to build the audience validator, per
	 * the reviewer's decompilation of Spring Boot 4.1.1) is present, non-empty, and behaves like a
	 * real audience validator when applied to a matching vs. a non-matching token.
	 *
	 * <p>ASSUNÇÃO: this reproduces {@code JwtDecoderConfiguration}'s {@code audienceValidator} logic
	 * ({@link JwtClaimValidator} over the {@code aud} claim, accepting when it intersects the
	 * configured audiences) instead of invoking the production {@link JwtDecoder} bean directly,
	 * because that bean is wired to the real {@code issuer-uri} and performs OIDC discovery over the
	 * network on first {@code decode()} — invoking it here would either require real network access
	 * to Auth0 (forbidden, {@code backlog/DIRECTIVES.md}) or mocking the HTTP client Nimbus uses
	 * internally, which would stop testing production wiring and start testing a mock. This is a
	 * deliberate, documented trade-off for partial-but-faithful coverage, not an oversight.
	 *
	 * <p>Confirmed red before being fixed: deleting the {@code audiences:} line from {@code
	 * application.yml} makes {@code resourceServerProperties.getJwt().getAudiences()} return {@code
	 * null}, failing the first assertion below, while the rest of the suite (43 tests) stayed green —
	 * exactly the blind spot the review reported.
	 */
	@Test
	void audienceValidationIsWiredFromProductionConfiguration() {
		List<String> configuredAudiences = resourceServerProperties.getJwt().getAudiences();

		assertThat(configuredAudiences)
				.as(
						"sem esta property (spring.security.oauth2.resourceserver.jwt.audiences, "
								+ "application.yml:24) o Boot nao acrescenta o validador de 'aud' "
								+ "(JwtDecoderConfiguration.getValidator) e qualquer token do tenant e aceite")
				.isNotNull()
				.isNotEmpty();

		OAuth2TokenValidator<Jwt> audienceValidator =
				new JwtClaimValidator<List<String>>(
						JwtClaimNames.AUD,
						audiences -> audiences != null && !Collections.disjoint(audiences, configuredAudiences));

		Jwt tokenForConfiguredAudience = jwtWithAudience(configuredAudiences.get(0));
		Jwt tokenForAnUnconfiguredAudience = jwtWithAudience("https://not-configured.example/");

		assertThat(audienceValidator.validate(tokenForConfiguredAudience).hasErrors()).isFalse();
		assertThat(audienceValidator.validate(tokenForAnUnconfiguredAudience).hasErrors()).isTrue();
	}

	/**
	 * Regression test for {@code backlog/reviews/TASK-007-r1.md}, IMPORTANTE 2: proves that a
	 * CORS preflight from an origin outside {@code app.cors.allowed-origins} is rejected by the real
	 * filter chain, and that the response carries no {@code Access-Control-Allow-Origin} header — the
	 * behaviour the reviewer confirmed manually and this test now fixes automatically.
	 *
	 * <p>Confirmed red before being fixed: removing {@code .cors(cors ->
	 * cors.configurationSource(corsConfigurationSource))} from {@code SecurityConfig.java} (the CORS
	 * bean stays declared but stops being applied to the chain) turned this preflight into a 401
	 * (no CORS filter to short-circuit it before the authentication filter), so the {@code
	 * isForbidden()} expectation failed while the rest of the suite stayed green.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void preflightFromAnUnlistedOriginIsRejected() throws Exception {
		mockMvc
				.perform(
						options(PROTECTED_PROBE_PATH)
								.header(HttpHeaders.ORIGIN, "https://evil.example")
								.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	/**
	 * Regression test for {@code backlog/reviews/TASK-007-r1.md}, IMPORTANTE 2: proves that a CORS
	 * preflight from the configured origin is allowed, and echoes that exact origin back — never a
	 * wildcard. This is what actually closes the gap the AC5 grep cannot: {@code
	 * setAllowedOrigins(List.of("*"))} would pass the grep, but would make this test fail (the
	 * response would allow {@code https://evil.example} too), since credentials are not required for
	 * this behavioural check to distinguish the two.
	 *
	 * <p>Confirmed red before being fixed, symmetrically with {@link
	 * #preflightFromAnUnlistedOriginIsRejected()}: with the same {@code .cors(...)} line removed, this
	 * preflight also turned into a 401 instead of 200.
	 *
	 * @throws Exception propagated from {@link MockMvc#perform}
	 */
	@Test
	void preflightFromTheConfiguredOriginIsAllowed() throws Exception {
		mockMvc
				.perform(
						options(PROTECTED_PROBE_PATH)
								.header(HttpHeaders.ORIGIN, "http://localhost:3000")
								.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
	}

	private static Jwt jwtWithAudience(String audience) {
		return Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject("auth0|test-user")
				.audience(List.of(audience))
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60))
				.build();
	}

	/**
	 * Swaps the production {@link JwtDecoder} (auto-configured by Spring Boot against the
	 * placeholder Auth0 issuer) for one backed by a local HMAC key, so any test in this class that
	 * needs a real decode-and-validate cycle can drive it without any network access, and registers
	 * the probe controller ({@link SecurityProbeController}) that lets those tests observe the
	 * authorities the security chain actually produced.
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

		/**
		 * @return the probe controller mapped to {@link SecurityConfigTest#PROTECTED_PROBE_PATH}
		 */
		@Bean
		SecurityProbeController securityProbeController() {
			return new SecurityProbeController();
		}
	}

	/**
	 * Test-only controller that reports the authorities of the currently authenticated request, so a
	 * test can assert on what the security chain actually produced (as opposed to only "not
	 * 401/403", which a mapped-but-empty endpoint or an unmapped 404 could also satisfy).
	 */
	@RestController
	static class SecurityProbeController {

		/**
		 * @return the authorities of the current {@link
		 *     org.springframework.security.core.context.SecurityContext}, as plain strings
		 */
		@GetMapping(PROTECTED_PROBE_PATH)
		List<String> authorities() {
			return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
					.map(GrantedAuthority::getAuthority)
					.toList();
		}
	}
}

package pt.diamondcars.dcbobackend.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Central, global security configuration for the back-office resource server.
 *
 * <p>Establishes the two rules every other task builds on top of (see {@code
 * backlog/tasks/TASK-007.md}, Overlap Conhecido): {@code /actuator/health} and {@code
 * /actuator/info} are public, everything else requires a valid Auth0-issued JWT. TASK-008,
 * TASK-009, TASK-011 and TASK-012 (business endpoints under {@code /api/**}) must never edit this
 * class to narrow or widen access further — role-specific rules belong in {@code @PreAuthorize} on
 * the controller method, which {@link EnableMethodSecurity} below makes available. TASK-010 is the
 * one deliberate exception: it adds {@link #internalFilterChain} below for {@code
 * InternalLeadController}, a path that is not a back-office endpoint at all (it is called by
 * {@code catalog-backend}, not a browser session), so it is authenticated by {@link
 * InternalTokenFilter} instead of a JWT.
 *
 * <p><b>{@code /internal/**} is its own {@link SecurityFilterChain}, not a {@code permitAll()}
 * rule on this one (BLOQUEADOR 1, {@code backlog/reviews/TASK-010-r1.md}).</b> The previous design
 * — a single chain with {@code .requestMatchers("/internal/**").permitAll()}, relying entirely on
 * {@link InternalTokenFilter} to reject unauthenticated requests before that rule was ever reached
 * — was fail-open: any divergence between the filter's own idea of "is this {@code /internal/**}?"
 * and the matcher {@code authorizeHttpRequests}/the {@code DispatcherServlet} use (which is exactly
 * what happened — see {@link InternalTokenFilter}'s Javadoc) turned {@code permitAll()} into
 * unauthenticated access. {@link #internalFilterChain} instead requires {@link
 * InternalTokenFilter#INTERNAL_SERVICE_AUTHORITY}, granted only by {@link InternalTokenFilter}
 * after a token match, so authorization is enforced by the same mechanism ({@code
 * authorizeHttpRequests}) as every other endpoint in this application, not bypassed for this one
 * path.
 *
 * <p>Audience validation of the JWT (a token issued for a different Auth0 API must be rejected)
 * is not implemented here: it is Spring Boot's own {@code
 * OAuth2ResourceServerAutoConfiguration}, driven by the {@code
 * spring.security.oauth2.resourceserver.jwt.audiences} property already present in {@code
 * application.yml}, that composes the issuer and audience validators into the auto-configured
 * {@link org.springframework.security.oauth2.jwt.JwtDecoder} bean. That auto-configured decoder is
 * lazily resolved (Spring Boot never performs the OIDC discovery network call at application
 * startup, only on first token decode), so declaring no {@code JwtDecoder} bean here is what keeps
 * every test that never sends an authenticated request (e.g. {@code
 * DcboBackendApplicationTests}) unaffected by the placeholder issuer configured for local/CI runs.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_ACTUATOR_PATHS = {
		"/actuator/health", "/actuator/health/**", "/actuator/info"
	};

	private static final String INTERNAL_PATH_PATTERN = "/internal/**";

	private static final List<String> ALLOWED_METHODS =
			List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

	/**
	 * Builds the dedicated {@code /internal/**} chain (BLOQUEADOR 1, {@code
	 * backlog/reviews/TASK-010-r1.md}): no {@code oauth2ResourceServer} wiring at all — {@link
	 * InternalTokenFilter} is the only authentication mechanism — and {@code authorizeHttpRequests}
	 * requires {@link InternalTokenFilter#INTERNAL_SERVICE_AUTHORITY} instead of {@code
	 * permitAll()}, so a request that ever reached {@code AuthorizationFilter} without that
	 * authority (e.g. if the filter were bypassed by some future routing quirk) is still denied.
	 * {@link Order @Order(1)} makes Spring Security try this chain, selected by {@link
	 * HttpSecurity#securityMatcher(String...)} (matched against the <em>decoded</em> path, same as
	 * {@link InternalTokenFilter}'s own matcher), before falling through to {@link
	 * #securityFilterChain}.
	 *
	 * @param http the {@link HttpSecurity} builder Spring Security provides for this chain
	 * @param internalTokenFilter guards every request on this chain with the shared {@code
	 *     CATALOG_SYNC_TOKEN} secret, registered ahead of where {@link
	 *     UsernamePasswordAuthenticationFilter} would sit in the standard filter order
	 * @return the configured filter chain, matching only {@value #INTERNAL_PATH_PATTERN}
	 * @throws Exception propagated from {@link HttpSecurity#build()} if the chain cannot be built
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain internalFilterChain(
			HttpSecurity http, InternalTokenFilter internalTokenFilter) throws Exception {
		http.securityMatcher(INTERNAL_PATH_PATTERN)
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(
						session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(
						authorize ->
								authorize
										.anyRequest()
										.hasAuthority(InternalTokenFilter.INTERNAL_SERVICE_AUTHORITY))
				.addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * Builds the general-purpose {@link SecurityFilterChain} of this resource server: stateless,
	 * CSRF-free (there is no browser session, only bearer tokens from the SPA), CORS-enabled for the
	 * configured `dcbo` origin(s), public actuator health/info, everything else authenticated via
	 * JWT. Never evaluated for {@code /internal/**} requests, which {@link #internalFilterChain}
	 * (tried first, {@link Order @Order(1)}) already claims via its own {@code securityMatcher}.
	 *
	 * @param http the {@link HttpSecurity} builder Spring Security provides
	 * @param jwtAuthenticationConverter converts a validated JWT into an {@link
	 *     org.springframework.security.core.Authentication} carrying the back-office roles as
	 *     authorities
	 * @param corsConfigurationSource the allowed-origins/methods/headers CORS policy
	 * @return the configured filter chain
	 * @throws Exception propagated from {@link HttpSecurity#build()} if the chain cannot be built
	 */
	@Bean
	@Order(2)
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtAuthenticationConverter jwtAuthenticationConverter,
			CorsConfigurationSource corsConfigurationSource)
			throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.sessionManagement(
						session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(
						authorize ->
								authorize.requestMatchers(PUBLIC_ACTUATOR_PATHS).permitAll().anyRequest().authenticated())
				.oauth2ResourceServer(
						oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
		return http.build();
	}

	/**
	 * Wires {@link Auth0RolesConverter} as the sole source of authorities extracted from a JWT,
	 * replacing Spring Security's default (which would otherwise map the {@code scope}/{@code scp}
	 * claim into {@code SCOPE_*} authorities that this domain does not use).
	 *
	 * @param auth0RolesConverter the roles-claim converter
	 * @return the configured {@link JwtAuthenticationConverter}
	 */
	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter(Auth0RolesConverter auth0RolesConverter) {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(auth0RolesConverter);
		return converter;
	}

	/**
	 * Declares the CORS policy allowing the configured `dcbo` origin(s) to call this API with a
	 * bearer token, per requirement 3 of TASK-007. Never registers a wildcard origin: {@code
	 * app.cors.allowed-origins} always lists explicit origins (comma-separated), even though this
	 * configuration does not enable credentialed requests (no cookies are used, only the {@code
	 * Authorization} header).
	 *
	 * @param allowedOrigins explicit origins allowed to call this API, from {@code
	 *     app.cors.allowed-origins} (default placeholder {@code http://localhost:3000})
	 * @return the CORS configuration source applied to every path
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(ALLOWED_METHODS);
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}

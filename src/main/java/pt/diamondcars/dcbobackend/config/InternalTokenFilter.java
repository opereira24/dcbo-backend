package pt.diamondcars.dcbobackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards every {@code /internal/**} request with the shared {@code CATALOG_SYNC_TOKEN} secret
 * instead of an Auth0 JWT (TASK-010, requirement 2): {@code catalog-backend} is a service, not a
 * back-office user, so it never holds a user JWT to present.
 *
 * <p><b>Fail-closed by design (BLOQUEADOR 1, {@code backlog/reviews/TASK-010-r1.md}).</b> Two
 * things used to make this endpoint reachable without any credential at all: this filter decided
 * "is this an {@code /internal/**} request?" by comparing {@link
 * HttpServletRequest#getRequestURI()} — the <em>raw, undecoded</em> path — against {@code
 * "/internal/"}, while {@code SecurityConfig}'s old {@code .requestMatchers("/internal/**")
 * .permitAll()} rule and the {@code DispatcherServlet} both compare against the <em>decoded</em>
 * path. A request to {@code /%69nternal/leads} (which decodes to {@code /internal/leads}) never
 * matched this filter's raw-string check, so it skipped the token check entirely, yet still
 * matched the {@code permitAll()} rule and reached {@link
 * pt.diamondcars.dcbobackend.web.internal.InternalLeadController} unauthenticated. Two changes
 * close this, together:
 *
 * <ol>
 *   <li>This filter now decides "is this {@code /internal/**}?" with the exact same matcher
 *       {@code SecurityConfig}'s dedicated internal {@code SecurityFilterChain} uses to select
 *       this filter chain in the first place ({@link #INTERNAL_PATH_MATCHER}, built from {@link
 *       PathPatternRequestMatcher}, which — like the {@code DispatcherServlet} — matches against
 *       the decoded path), instead of a bespoke raw-string comparison.
 *   <li>{@code SecurityConfig} no longer {@code permitAll()}s {@code /internal/**}: it requires
 *       the {@link #INTERNAL_SERVICE_AUTHORITY} authority this filter grants only after a token
 *       match, on a chain with no {@code oauth2ResourceServer} wiring at all. If this filter were
 *       ever bypassed by some future routing quirk, the request would reach {@code
 *       AuthorizationFilter} with no such authority and still be rejected — authorization no
 *       longer depends solely on this filter running first and deciding "this is public".
 * </ol>
 *
 * <p>Runs for every request (a plain {@link OncePerRequestFilter}, not scoped by a {@code
 * securityMatcher}), but only actually inspects paths matching {@link #INTERNAL_PATH_MATCHER} —
 * every other request passes straight through unaffected, leaving the Auth0 JWT resource-server
 * chain (TASK-007) as the sole gate for {@code /api/**}. Registered by {@code SecurityConfig}
 * ahead of where {@code UsernamePasswordAuthenticationFilter} would sit in the standard filter
 * order, on the dedicated {@code /internal/**} chain only, so an invalid/missing token
 * short-circuits with 401 before Spring Security's authorization decision (and, on the {@code
 * /api/**} chain, before any JWT processing) ever runs — this is what makes a valid Auth0 JWT
 * without the shared token still result in 401 (acceptance criterion 5): this filter never even
 * looks at the {@code Authorization} header.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);

	/**
	 * Authority granted to the {@link SecurityContextHolder} when — and only when — a request
	 * presents the correct shared token. {@code SecurityConfig}'s dedicated {@code /internal/**}
	 * filter chain requires exactly this authority instead of {@code permitAll()} (BLOQUEADOR 1).
	 * Deliberately outside the {@code ROLE_*} shape {@link Auth0RolesConverter} produces from a JWT
	 * roles claim, so a back-office JWT — however it was obtained — can never satisfy this check.
	 */
	static final String INTERNAL_SERVICE_AUTHORITY = "INTERNAL_SERVICE";

	/**
	 * The same kind of matcher {@code SecurityConfig}'s {@code securityMatcher("/internal/**")} uses
	 * to select the internal filter chain — evaluated against the <em>decoded</em> path, unlike the
	 * raw {@link HttpServletRequest#getRequestURI()} the previous implementation compared against
	 * (see the class Javadoc for why that mismatch was the root cause of BLOQUEADOR 1).
	 */
	private static final RequestMatcher INTERNAL_PATH_MATCHER =
			PathPatternRequestMatcher.withDefaults().matcher("/internal/**");

	private static final String TOKEN_HEADER = "X-Internal-Token";

	/**
	 * Minimum length a configured token must have to be treated as a real secret rather than a
	 * forgotten placeholder (IMPORTANTE 1, {@code backlog/reviews/TASK-010-r1.md}).
	 */
	private static final int MIN_TOKEN_LENGTH = 32;

	/**
	 * The literal default {@code application.yml} falls back to when {@code CATALOG_SYNC_TOKEN} is
	 * not set. Never accepted as a valid secret to authenticate with — even if a future edit made it
	 * {@link #MIN_TOKEN_LENGTH} characters or longer — because it is visible in the repository
	 * itself and therefore not a secret at all.
	 */
	private static final String PLACEHOLDER_TOKEN = "placeholder";

	private final String expectedToken;

	/**
	 * {@code true} only when {@link #expectedToken} is non-blank, is not {@link #PLACEHOLDER_TOKEN},
	 * and is at least {@link #MIN_TOKEN_LENGTH} characters long. When {@code false}, every {@code
	 * /internal/**} request is rejected regardless of the header presented (IMPORTANTE 1): a missing
	 * {@code CATALOG_SYNC_TOKEN} environment variable (e.g. forgotten in Render) must fail closed,
	 * never silently accept the placeholder default that ships in the repository.
	 */
	private final boolean tokenIsSecure;

	/**
	 * Creates the filter bound to the configured shared token, logging an error at startup when that
	 * token is not a real secret (see {@link #tokenIsSecure}).
	 *
	 * @param expectedToken the secret every {@code /internal/**} request must present via {@link
	 *     #TOKEN_HEADER}, injected from {@code catalog.sync.internal-token} (placeholder default,
	 *     see {@code application.yml})
	 */
	public InternalTokenFilter(@Value("${catalog.sync.internal-token}") String expectedToken) {
		this.expectedToken = expectedToken;
		this.tokenIsSecure = isRealSecret(expectedToken);
		if (!tokenIsSecure) {
			log.error(
					"catalog.sync.internal-token is not configured with a real secret (missing, blank, "
							+ "the '{}' placeholder, or shorter than {} characters) - every /internal/** "
							+ "request will be rejected until CATALOG_SYNC_TOKEN is set to a real secret",
					PLACEHOLDER_TOKEN,
					MIN_TOKEN_LENGTH);
		}
	}

	/**
	 * Decides whether a configured token value is fit to authenticate {@code /internal/**} requests.
	 *
	 * @param token the configured value of {@code catalog.sync.internal-token}
	 * @return {@code false} when {@code token} is {@code null}, blank, equal to {@link
	 *     #PLACEHOLDER_TOKEN}, or shorter than {@link #MIN_TOKEN_LENGTH} characters
	 */
	private static boolean isRealSecret(String token) {
		return token != null
				&& !token.isBlank()
				&& !PLACEHOLDER_TOKEN.equals(token)
				&& token.length() >= MIN_TOKEN_LENGTH;
	}

	/**
	 * Rejects with 401 any {@code /internal/**} request whose {@value #TOKEN_HEADER} header does not
	 * exactly match the configured secret, or whose configured secret is not a real one (see {@link
	 * #tokenIsSecure}); on a match, grants {@link #INTERNAL_SERVICE_AUTHORITY} on the {@link
	 * SecurityContextHolder} before continuing the chain. Every other request (including non-{@code
	 * /internal/**} requests) passes through unchanged.
	 *
	 * @param request the incoming request
	 * @param response the response, written to directly (401, no body parsing by a later handler) on
	 *     rejection
	 * @param filterChain the rest of the filter chain, invoked only when the request is not under
	 *     {@link #INTERNAL_PATH_MATCHER} or the token matches
	 * @throws ServletException propagated from {@link FilterChain#doFilter}
	 * @throws IOException propagated from {@link FilterChain#doFilter} or writing the 401 response
	 */
	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!INTERNAL_PATH_MATCHER.matches(request)) {
			filterChain.doFilter(request, response);
			return;
		}
		if (tokenIsSecure && matchesExpectedToken(request.getHeader(TOKEN_HEADER))) {
			SecurityContextHolder.getContext()
					.setAuthentication(
							new UsernamePasswordAuthenticationToken(
									"catalog-backend",
									null,
									List.of(new SimpleGrantedAuthority(INTERNAL_SERVICE_AUTHORITY))));
			filterChain.doFilter(request, response);
			return;
		}
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"message\":\"Token interno invalido ou ausente\"}");
	}

	/**
	 * Compares the presented header value against {@link #expectedToken} in constant time.
	 *
	 * @param providedToken the {@value #TOKEN_HEADER} header value, or {@code null} when absent
	 * @return {@code true} only when {@code providedToken} is non-null and byte-for-byte equal to
	 *     {@link #expectedToken}
	 */
	private boolean matchesExpectedToken(String providedToken) {
		return providedToken != null
				&& MessageDigest.isEqual(
						providedToken.getBytes(StandardCharsets.UTF_8),
						expectedToken.getBytes(StandardCharsets.UTF_8));
	}
}

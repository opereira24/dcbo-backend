package pt.diamondcars.dcbobackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards every {@code /internal/**} request with the shared {@code CATALOG_SYNC_TOKEN} secret
 * instead of an Auth0 JWT (TASK-010, requirement 2): {@code catalog-backend} is a service, not a
 * back-office user, so it never holds a user JWT to present.
 *
 * <p>Runs for every request (a plain {@link OncePerRequestFilter}, not scoped by a {@code
 * securityMatcher}), but only actually inspects paths under {@link #INTERNAL_PATH_PREFIX} — every
 * other request passes straight through unaffected, leaving the Auth0 JWT resource-server chain
 * (TASK-007) as the sole gate for {@code /api/**}. Registered by {@code SecurityConfig} ahead of
 * where {@code UsernamePasswordAuthenticationFilter} would sit in the standard filter order, so an
 * invalid/missing token short-circuits with 401 before Spring Security's JWT processing (and the
 * {@code /internal/**} {@code permitAll()} rule that processing would otherwise honour) ever runs —
 * this is what makes a valid Auth0 JWT without the shared token still result in 401 (acceptance
 * criterion 5): this filter never even looks at the {@code Authorization} header.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

	private static final String INTERNAL_PATH_PREFIX = "/internal/";
	private static final String TOKEN_HEADER = "X-Internal-Token";

	private final String expectedToken;

	/**
	 * Creates the filter bound to the configured shared token.
	 *
	 * @param expectedToken the secret every {@code /internal/**} request must present via {@link
	 *     #TOKEN_HEADER}, injected from {@code catalog.sync.internal-token} (placeholder default,
	 *     see {@code application.yml})
	 */
	public InternalTokenFilter(@Value("${catalog.sync.internal-token}") String expectedToken) {
		this.expectedToken = expectedToken;
	}

	/**
	 * Rejects with 401 any {@code /internal/**} request whose {@value #TOKEN_HEADER} header does not
	 * exactly match the configured secret; every other request (including non-{@code /internal/**}
	 * requests) passes through unchanged.
	 *
	 * @param request the incoming request
	 * @param response the response, written to directly (401, no body parsing by a later handler) on
	 *     rejection
	 * @param filterChain the rest of the filter chain, invoked only when the request is not under
	 *     {@link #INTERNAL_PATH_PREFIX} or the token matches
	 * @throws ServletException propagated from {@link FilterChain#doFilter}
	 * @throws IOException propagated from {@link FilterChain#doFilter} or writing the 401 response
	 */
	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}
		String providedToken = request.getHeader(TOKEN_HEADER);
		if (providedToken == null || !providedToken.equals(expectedToken)) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write("{\"message\":\"Token interno invalido ou ausente\"}");
			return;
		}
		filterChain.doFilter(request, response);
	}
}

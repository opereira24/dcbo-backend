package pt.diamondcars.dcbobackend.config;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Gives services access to the identity of the currently authenticated back-office user without
 * repeating {@link SecurityContextHolder} lookups in every service of the tasks that follow
 * (TASK-008 to TASK-012), which need it to register authorship (e.g. who created a lead, who sold
 * a car).
 *
 * <p>The identity is always the Auth0 {@code sub} claim of the request's JWT, the same value
 * {@link pt.diamondcars.dcbobackend.domain.user.AppUser#getAuthSubject()} is matched against.
 */
@Component
public class AuthenticatedUserProvider {

	/**
	 * Returns the {@code sub} claim of the JWT backing the current request's authentication, if
	 * any.
	 *
	 * @return the Auth0 subject of the currently authenticated user, or {@link Optional#empty()}
	 *     when there is no authenticated JWT in the current {@link SecurityContextHolder} (e.g. a
	 *     call made outside an HTTP request, or to a permitted, unauthenticated endpoint)
	 */
	public Optional<String> getCurrentUserSubject() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
			return Optional.ofNullable(jwtAuthenticationToken.getToken().getSubject());
		}
		return Optional.empty();
	}
}

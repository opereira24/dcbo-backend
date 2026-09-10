package pt.diamondcars.dcbobackend.config;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Converts the namespaced Auth0 roles claim of an access token into Spring Security {@link
 * GrantedAuthority} instances, so that {@code @PreAuthorize("hasRole('ADMIN')")} (or equivalent)
 * works in controllers of later tasks (TASK-008 to TASK-012) without any of them needing to touch
 * {@link SecurityConfig} again.
 *
 * <p>The claim name is not hardcoded: Auth0 namespaces custom claims under a URL that depends on
 * the tenant the user creates manually (see {@code backlog/DIRECTIVES.md}), so it is read from
 * {@code app.auth0.roles-claim} (default placeholder {@code https://diamondcars/roles}) instead.
 * The claim values themselves are expected to match {@code USER_ROLES} in {@code
 * dcbo/src/services/userManagementService.js:124-131} ({@code admin}, {@code user}), which this
 * converter upper-cases before prefixing with {@code ROLE_}, matching the case Spring Security's
 * {@code hasRole(...)} expects.
 */
@Component
public class Auth0RolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	private final String rolesClaim;

	/**
	 * Creates the converter bound to the configured roles claim name.
	 *
	 * @param rolesClaim name of the namespaced claim Auth0 uses for back-office roles, injected
	 *     from {@code app.auth0.roles-claim}
	 */
	public Auth0RolesConverter(@Value("${app.auth0.roles-claim}") String rolesClaim) {
		this.rolesClaim = rolesClaim;
	}

	/**
	 * Reads the roles claim of the given JWT and maps every non-blank value to a {@code
	 * ROLE_<VALUE>} authority, upper-cased.
	 *
	 * @param jwt the decoded, already-validated access token
	 * @return the resulting authorities, or an empty collection if the claim is absent or empty
	 */
	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList(rolesClaim);
		if (roles == null || roles.isEmpty()) {
			return List.of();
		}
		return roles.stream()
				.filter(StringUtils::hasText)
				.map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
				.collect(Collectors.toUnmodifiableList());
	}
}

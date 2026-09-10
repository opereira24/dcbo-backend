package pt.diamondcars.dcbobackend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit test of {@link Auth0RolesConverter} (TASK-007 acceptance criterion 4): does not load any
 * Spring context, only exercises the claim-to-authority mapping directly.
 */
class Auth0RolesConverterTest {

	private static final String ROLES_CLAIM = "https://diamondcars/roles";

	private final Auth0RolesConverter converter = new Auth0RolesConverter(ROLES_CLAIM);

	/**
	 * Confirms every value of the configured roles claim becomes a {@code ROLE_<VALUE>} authority,
	 * upper-cased, matching the two values {@code AppUserRole} persists ({@code admin}, {@code
	 * user}).
	 *
	 * @throws AssertionError if the resulting authorities do not match exactly
	 */
	@Test
	void mapsEachRolesClaimValueToAnUppercaseRoleAuthority() {
		Jwt jwt = jwtWithRoles(List.of("admin", "user"));

		assertThat(converter.convert(jwt))
				.containsExactlyInAnyOrder(
						new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"));
	}

	/**
	 * Confirms a JWT without the configured roles claim yields no authorities at all, instead of
	 * failing.
	 *
	 * @throws AssertionError if any authority is produced
	 */
	@Test
	void returnsNoAuthoritiesWhenTheRolesClaimIsAbsent() {
		Jwt jwt = jwtWithRoles(null);

		assertThat(converter.convert(jwt)).isEmpty();
	}

	private static Jwt jwtWithRoles(List<String> roles) {
		Jwt.Builder builder =
				Jwt.withTokenValue("token")
						.header("alg", "none")
						.subject("auth0|test-user")
						.issuedAt(Instant.now())
						.expiresAt(Instant.now().plusSeconds(60));
		if (roles != null) {
			builder.claim(ROLES_CLAIM, roles);
		}
		return builder.build();
	}
}

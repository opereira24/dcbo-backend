package pt.diamondcars.dcbobackend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.GrantedAuthority;
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
		Jwt jwt = jwtWithClaimValue(List.of("admin", "user"));

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
		Jwt jwt = jwtWithClaimValue(null);

		assertThat(converter.convert(jwt)).isEmpty();
	}

	/**
	 * Regression test for {@code backlog/reviews/TASK-007-r1.md}, IMPORTANTE 3: exercises every
	 * shape of the roles claim the reviewer identified by mutation, including the ones the previous
	 * two tests left untouched ({@code filter(hasText)} on {@code Auth0RolesConverter.java:57} in
	 * particular, never exercised before this test existed). The expected authorities below are
	 * exactly the ones the reviewer observed by running the real converter against each shape,
	 * copied from {@code backlog/reviews/TASK-007-r1.md} ("Ponto 5 do pedido").
	 *
	 * @param description human-readable label for the parameterized case, shown in the test report
	 * @param claimValue the raw value the {@code https://diamondcars/roles} claim would carry
	 * @param expectedAuthorities the authorities the converter must produce for that value
	 * @throws AssertionError if the resulting authorities do not match exactly
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("rolesClaimShapes")
	void mapsEveryObservedShapeOfTheRolesClaimWithoutThrowing(
			String description, Object claimValue, List<GrantedAuthority> expectedAuthorities) {
		Jwt jwt = jwtWithClaimValue(claimValue);

		assertThat(converter.convert(jwt)).containsExactlyInAnyOrderElementsOf(expectedAuthorities);
	}

	private static Stream<Arguments> rolesClaimShapes() {
		return Stream.of(
				Arguments.of("lista vazia", List.of(), List.of()),
				Arguments.of(
						"string simples em vez de lista",
						"admin",
						List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))),
				Arguments.of(
						"lista com valores nulos/em branco (exercita o filter(hasText))",
						Arrays.asList("admin", null, "  "),
						List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))),
				Arguments.of(
						"objeto em vez de string/lista",
						Map.of("role", "admin"),
						List.of(new SimpleGrantedAuthority("ROLE_{ROLE=ADMIN}"))),
				Arguments.of(
						"numeros em vez de strings",
						List.of(1, 2),
						List.of(
								new SimpleGrantedAuthority("ROLE_1"), new SimpleGrantedAuthority("ROLE_2"))));
	}

	private static Jwt jwtWithClaimValue(Object claimValue) {
		Jwt.Builder builder =
				Jwt.withTokenValue("token")
						.header("alg", "none")
						.subject("auth0|test-user")
						.issuedAt(Instant.now())
						.expiresAt(Instant.now().plusSeconds(60));
		if (claimValue != null) {
			builder.claim(ROLES_CLAIM, claimValue);
		}
		return builder.build();
	}
}

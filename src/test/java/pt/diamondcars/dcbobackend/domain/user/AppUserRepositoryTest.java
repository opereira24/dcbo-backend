package pt.diamondcars.dcbobackend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;

/**
 * {@code @DataJpaTest} for the {@link AppUser} aggregate: confirms it round-trips through the real
 * {@code app_users} table (TASK-006 requirement 10), that the {@link AppUserRole} converter
 * survives a save/reload cycle, and exercises {@link AppUserRepository#findByAuthSubject(String)}
 * (requirement 7).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AppUserRepositoryTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private AppUserRepository appUserRepository;

	/**
	 * Confirms an {@link AppUser} saved with a {@link AppUserRole} enum value can be reloaded with
	 * that same enum constant intact, and with its database-matching default ({@code active =
	 * true}).
	 *
	 * @throws AssertionError if the reloaded user does not match what was saved
	 */
	@Test
	void savesAndReloadsAnAppUserWithItsRole() {
		String authSubject = "auth0|" + UUID.randomUUID();

		AppUser saved = appUserRepository.save(AppUser.builder()
				.authSubject(authSubject)
				.email("admin@diamondcars.pt")
				.name("Admin User")
				.role(AppUserRole.ADMIN)
				.build());

		AppUser reloaded = appUserRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getRole()).isEqualTo(AppUserRole.ADMIN);
		assertThat(reloaded.isActive()).isTrue();
	}

	/**
	 * Confirms {@link AppUserRepository#findByAuthSubject(String)} finds a user by their Auth0
	 * subject and returns empty for a subject that was never persisted.
	 *
	 * @throws AssertionError if either lookup returns the wrong result
	 */
	@Test
	void findsAUserByAuthSubject() {
		String authSubject = "auth0|" + UUID.randomUUID();
		appUserRepository.save(AppUser.builder()
				.authSubject(authSubject)
				.email("user@diamondcars.pt")
				.name("Regular User")
				.role(AppUserRole.USER)
				.build());

		assertThat(appUserRepository.findByAuthSubject(authSubject))
				.isPresent()
				.get()
				.extracting(AppUser::getEmail)
				.isEqualTo("user@diamondcars.pt");

		assertThat(appUserRepository.findByAuthSubject("auth0|does-not-exist")).isEmpty();
	}
}

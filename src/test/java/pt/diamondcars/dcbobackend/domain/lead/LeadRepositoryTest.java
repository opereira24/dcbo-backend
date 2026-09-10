package pt.diamondcars.dcbobackend.domain.lead;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import pt.diamondcars.dcbobackend.domain.car.Car;
import pt.diamondcars.dcbobackend.domain.car.CarRepository;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;

/**
 * {@code @DataJpaTest} for the {@link Lead} aggregate: confirms it round-trips through the real
 * {@code leads} table (TASK-006 requirement 10), that its declared defaults ({@link
 * LeadStatus#CONTACTADO}, {@link LeadOrigin#WEBSITE}) survive a save/reload cycle that actually
 * hits the database via their converters, and exercises {@link
 * LeadRepository#findByCarIdOrderByCreatedAtDesc(java.util.UUID)} (requirement 7).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LeadRepositoryTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private LeadRepository leadRepository;

	@Autowired
	private CarRepository carRepository;

	@PersistenceContext
	private EntityManager entityManager;

	private static Car aCar() {
		return Car.builder()
				.marca("Mercedes")
				.modelo("C220")
				.ano(2021)
				.preco(new BigDecimal("30000.00"))
				.km(30000)
				.cor("Cinza")
				.combustivel("Diesel")
				.transmissao("Automatica")
				.origem("stand")
				.build();
	}

	/**
	 * Confirms a {@link Lead} saved with only its required fields set can be reloaded with its
	 * database-matching defaults ({@link LeadStatus#CONTACTADO}, {@link LeadOrigin#WEBSITE}) intact.
	 *
	 * <p>Uses {@code saveAndFlush} and then clears the persistence context before reloading: a
	 * plain {@code save} followed by {@code findById} inside the same transaction would be served
	 * entirely by Hibernate's first-level cache — no {@code INSERT} or {@code SELECT} would ever
	 * reach the database, and the assertions below would pass regardless of whether the mapping or
	 * the enum converters are even correct.
	 *
	 * @throws AssertionError if the reloaded lead does not match what was saved
	 */
	@Test
	void savesAndReloadsALeadWithDefaults() {
		Lead saved = leadRepository.saveAndFlush(
				Lead.builder().nome("Joao Cliente").telefone("913456789").build());
		entityManager.clear();

		Lead reloaded = leadRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded).isNotSameAs(saved);
		assertThat(reloaded.getStatus()).isEqualTo(LeadStatus.CONTACTADO);
		assertThat(reloaded.getOrigem()).isEqualTo(LeadOrigin.WEBSITE);
	}

	/**
	 * Confirms {@link LeadRepository#findByCarIdOrderByCreatedAtDesc(java.util.UUID)} returns only
	 * the leads about the given car, most recently created first.
	 *
	 * @throws AssertionError if a lead about a different car is included, or the order is wrong
	 */
	@Test
	void findsLeadsAboutACarMostRecentlyCreatedFirst() {
		Car car = carRepository.saveAndFlush(aCar());
		Car otherCar = carRepository.saveAndFlush(aCar());

		Lead first = leadRepository.save(
				Lead.builder().nome("Primeiro Lead").telefone("911111111").car(car).build());
		Lead second = leadRepository.save(
				Lead.builder().nome("Segundo Lead").telefone("922222222").car(car).build());
		leadRepository.save(
				Lead.builder().nome("Lead Outro Carro").telefone("933333333").car(otherCar).build());

		assertThat(leadRepository.findByCarIdOrderByCreatedAtDesc(car.getId()))
				.extracting(Lead::getId)
				.containsExactly(second.getId(), first.getId());
	}
}

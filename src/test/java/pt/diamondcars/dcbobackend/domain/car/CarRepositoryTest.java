package pt.diamondcars.dcbobackend.domain.car;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import pt.diamondcars.dcbobackend.support.AbstractPostgresIntegrationTest;

/**
 * {@code @DataJpaTest} for the {@link Car} aggregate (including its {@link CarImage} child
 * entity): confirms it round-trips through the real {@code cars}/{@code car_images} tables
 * (TASK-006 requirement 10) and exercises every derived query TASK-006 requirement 7 lists for
 * this aggregate.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CarRepositoryTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private CarRepository carRepository;

	@PersistenceContext
	private EntityManager entityManager;

	private static Car.CarBuilder aCar() {
		return Car.builder()
				.marca("BMW")
				.modelo("320d")
				.ano(2020)
				.preco(new BigDecimal("25000.00"))
				.km(50000)
				.cor("Preto")
				.combustivel("Diesel")
				.transmissao("Automatica")
				.origem("stand");
	}

	/**
	 * Confirms a {@link Car} saved with its child {@link CarImage}s (via {@link
	 * Car#addImage(CarImage)}) round-trips both the car's declared defaults ({@code garantia_meses
	 * = 0}, {@code destaque}/{@code vendido}/{@code reservado}/{@code is_consignacao = false}) and
	 * the images themselves, in {@code position} order.
	 *
	 * <p>Clears the persistence context after flushing and before reloading: {@code images} is
	 * added in reverse position order on purpose, and {@code @OrderBy("position ASC")} only takes
	 * effect when Hibernate actually re-runs the collection query — an already-initialized
	 * in-memory collection on a still-managed entity would otherwise be returned as-is, in
	 * insertion order, silently hiding an incorrect {@code @OrderBy}.
	 *
	 * @throws AssertionError if the reloaded car or its images do not match what was saved
	 */
	@Test
	void savesAndReloadsACarWithItsImagesInPositionOrder() {
		Car car = aCar().build();
		car.addImage(CarImage.builder().url("https://img/2.jpg").position(2).build());
		car.addImage(CarImage.builder().url("https://img/1.jpg").position(1).build());

		Car saved = carRepository.saveAndFlush(car);
		entityManager.clear();

		Car reloaded = carRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getGarantiaMeses()).isZero();
		assertThat(reloaded.isDestaque()).isFalse();
		assertThat(reloaded.isVendido()).isFalse();
		assertThat(reloaded.isReservado()).isFalse();
		assertThat(reloaded.isConsignacao()).isFalse();
		assertThat(reloaded.getImages())
				.extracting(CarImage::getUrl)
				.containsExactly("https://img/1.jpg", "https://img/2.jpg");
	}

	/**
	 * Confirms {@link CarRepository#findAllByOrderByCreatedAtDesc()} returns every car, most
	 * recently created first.
	 *
	 * @throws AssertionError if the returned order does not match creation order (descending)
	 */
	@Test
	void listsAllCarsMostRecentlyCreatedFirst() {
		Car first = carRepository.saveAndFlush(aCar().modelo("Serie 1").build());
		Car second = carRepository.saveAndFlush(aCar().modelo("Serie 3").build());

		assertThat(carRepository.findAllByOrderByCreatedAtDesc())
				.extracting(Car::getId)
				.containsExactly(second.getId(), first.getId());
	}

	/**
	 * Confirms {@link CarRepository#findByVendidoFalseOrderByCreatedAtDesc()} excludes sold cars.
	 *
	 * @throws AssertionError if a sold car is included, or an unsold one is missing
	 */
	@Test
	void listsOnlyUnsoldCars() {
		Car unsold = carRepository.saveAndFlush(aCar().modelo("Unsold").build());
		carRepository.saveAndFlush(aCar().modelo("Sold").vendido(true).build());

		assertThat(carRepository.findByVendidoFalseOrderByCreatedAtDesc())
				.extracting(Car::getId)
				.containsExactly(unsold.getId());
	}

	/**
	 * Confirms {@link CarRepository#findByDestaqueTrue()} returns only highlighted cars.
	 *
	 * @throws AssertionError if a non-highlighted car is included, or a highlighted one is missing
	 */
	@Test
	void listsOnlyHighlightedCars() {
		Car highlighted = carRepository.saveAndFlush(aCar().modelo("Highlighted").destaque(true).build());
		carRepository.saveAndFlush(aCar().modelo("Regular").build());

		assertThat(carRepository.findByDestaqueTrue())
				.extracting(Car::getId)
				.containsExactly(highlighted.getId());
	}
}

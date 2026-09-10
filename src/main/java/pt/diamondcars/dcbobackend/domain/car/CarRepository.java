package pt.diamondcars.dcbobackend.domain.car;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Car}, with the derived queries TASK-006 requirement 7 lists as
 * needed by the existing frontends.
 */
public interface CarRepository extends JpaRepository<Car, UUID> {

	/**
	 * Lists every car, most recently created first — equivalent to the back-office listing order in
	 * {@code dcbo/src/services/firebaseService.js:32}.
	 *
	 * @return all cars ordered by {@code createdAt} descending
	 */
	List<Car> findAllByOrderByCreatedAtDesc();

	/**
	 * Lists cars not yet sold, most recently created first — the set shown on the public site,
	 * equivalent to {@code dc/src/services/firebaseService.js:24}.
	 *
	 * @return cars where {@code vendido = false}, ordered by {@code createdAt} descending
	 */
	List<Car> findByVendidoFalseOrderByCreatedAtDesc();

	/**
	 * Lists cars flagged as highlighted, equivalent to {@code dcbo/src/pages/highlights.js:5}.
	 *
	 * @return cars where {@code destaque = true}
	 */
	List<Car> findByDestaqueTrue();
}

package pt.diamondcars.dcbobackend.domain.car;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Spring Data repository for {@link Car}, with the derived queries TASK-006 requirement 7 lists as
 * needed by the existing frontends, plus {@link JpaSpecificationExecutor} (TASK-008 requirement 1)
 * so {@code GET /api/cars} can combine its optional {@code vendido}/{@code reservado}/{@code
 * destaque} filters without one derived-query method per combination.
 */
public interface CarRepository extends JpaRepository<Car, UUID>, JpaSpecificationExecutor<Car> {

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

	/**
	 * Counts how many cars are currently featured, the value {@code
	 * pt.diamondcars.dcbobackend.service.CarService} compares against the 8-car limit (TASK-008
	 * requirement 1) before allowing one more car to be featured.
	 *
	 * @return the number of cars with {@code destaque = true}
	 */
	long countByDestaqueTrue();

	/**
	 * Lists cars purchased by a given client, most recently created first — backs {@code
	 * GET /api/clients/{id}/cars} (TASK-009, requirement 1), which supports the modal {@code
	 * dcbo/src/components/client-cars-modal.js}.
	 *
	 * @param clientId the {@link pt.diamondcars.dcbobackend.domain.client.Client} id to filter by
	 * @return cars whose {@code client_id} equals {@code clientId}, ordered by {@code createdAt}
	 *     descending
	 */
	List<Car> findByClientIdOrderByCreatedAtDesc(UUID clientId);

	/**
	 * Tests whether any car references a given client, used by {@code ClientService} to refuse
	 * deleting a client that has purchased cars (TASK-009, requirement 1) instead of deleting it
	 * silently.
	 *
	 * @param clientId the {@link pt.diamondcars.dcbobackend.domain.client.Client} id to check
	 * @return {@code true} if at least one car has this {@code client_id}
	 */
	boolean existsByClientId(UUID clientId);

	/**
	 * Lists cars in consignment for a given partner, most recently created first — backs {@code
	 * GET /api/partners/{id}/cars} (TASK-009, requirement 4).
	 *
	 * @param partnerId the {@link pt.diamondcars.dcbobackend.domain.partner.Partner} id to filter by
	 * @return cars whose {@code partner_id} equals {@code partnerId}, ordered by {@code createdAt}
	 *     descending
	 */
	List<Car> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId);

	/**
	 * Tests whether any car references a given partner, used by {@code PartnerService} to refuse
	 * deleting a partner that has consignment cars (TASK-009, requirement 4) instead of deleting it
	 * silently.
	 *
	 * @param partnerId the {@link pt.diamondcars.dcbobackend.domain.partner.Partner} id to check
	 * @return {@code true} if at least one car has this {@code partner_id}
	 */
	boolean existsByPartnerId(UUID partnerId);
}

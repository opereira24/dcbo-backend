package pt.diamondcars.dcbobackend.domain.lead;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Lead}, with the derived query TASK-006 requirement 7 lists as
 * needed by the existing frontend.
 */
public interface LeadRepository extends JpaRepository<Lead, UUID> {

	/**
	 * Lists every lead about a given car, most recently created first — equivalent to {@code
	 * getCarLeads} in {@code dcbo/src/services/firebaseService.js:460}.
	 *
	 * @param carId the {@link pt.diamondcars.dcbobackend.domain.car.Car} id to filter by
	 * @return leads whose {@code car_id} equals {@code carId}, ordered by {@code createdAt}
	 *         descending
	 */
	List<Lead> findByCarIdOrderByCreatedAtDesc(UUID carId);
}

package pt.diamondcars.dcbobackend.domain.transaction;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Transaction}, with the derived query TASK-006 requirement 7
 * lists as needed by the existing frontend.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

	/**
	 * Lists every transaction linked to a given car, equivalent to {@code deleteCarTransactions} in
	 * {@code dcbo/src/services/firebaseService.js:415}.
	 *
	 * @param carId the {@link pt.diamondcars.dcbobackend.domain.car.Car} id to filter by
	 * @return all transactions whose {@code car_id} equals {@code carId}
	 */
	List<Transaction> findByCarId(UUID carId);
}

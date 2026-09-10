package pt.diamondcars.dcbobackend.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import pt.diamondcars.dcbobackend.domain.car.Car;

/**
 * Builds the {@link Specification} {@link CarService#list} uses to combine the optional {@code
 * GET /api/cars} filters (TASK-008, requirement 1), so any subset of {@code vendido}/{@code
 * reservado}/{@code destaque} can be requested together without one derived-query method per
 * combination.
 */
final class CarSpecifications {

	private CarSpecifications() {}

	/**
	 * Builds a specification that matches every car whose {@code vendido}/{@code reservado}/{@code
	 * destaque} column equals the corresponding non-{@code null} argument. A {@code null} argument
	 * is not filtered on at all (not "match false").
	 *
	 * @param vendido required value of {@code cars.vendido}, or {@code null} to not filter by it
	 * @param reservado required value of {@code cars.reservado}, or {@code null} to not filter by it
	 * @param destaque required value of {@code cars.destaque}, or {@code null} to not filter by it
	 * @return the combined specification; matches every car when all three arguments are {@code
	 *     null}
	 */
	static Specification<Car> matching(Boolean vendido, Boolean reservado, Boolean destaque) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (vendido != null) {
				predicates.add(criteriaBuilder.equal(root.get("vendido"), vendido));
			}
			if (reservado != null) {
				predicates.add(criteriaBuilder.equal(root.get("reservado"), reservado));
			}
			if (destaque != null) {
				predicates.add(criteriaBuilder.equal(root.get("destaque"), destaque));
			}
			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		};
	}
}

package pt.diamondcars.dcbobackend.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import pt.diamondcars.dcbobackend.domain.lead.Lead;
import pt.diamondcars.dcbobackend.domain.lead.LeadStatus;

/**
 * Builds the {@link Specification} {@link LeadService#list} uses to combine the optional {@code
 * GET /api/leads} filters (TASK-010, requirement 1), so {@code status} and {@code carroId} can be
 * requested together or separately without one derived-query method per combination.
 */
final class LeadSpecifications {

	private LeadSpecifications() {}

	/**
	 * Builds a specification that matches every lead whose {@code status}/{@code car_id} column
	 * equals the corresponding non-{@code null} argument. A {@code null} argument is not filtered on
	 * at all (not "match nothing").
	 *
	 * @param status required value of {@code leads.status}, or {@code null} to not filter by it
	 * @param carId required value of {@code leads.car_id}, or {@code null} to not filter by it
	 * @return the combined specification; matches every lead when both arguments are {@code null}
	 */
	static Specification<Lead> matching(LeadStatus status, UUID carId) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) {
				predicates.add(criteriaBuilder.equal(root.get("status"), status));
			}
			if (carId != null) {
				predicates.add(criteriaBuilder.equal(root.get("car").get("id"), carId));
			}
			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		};
	}
}

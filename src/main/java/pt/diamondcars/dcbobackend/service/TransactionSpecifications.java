package pt.diamondcars.dcbobackend.service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import pt.diamondcars.dcbobackend.domain.transaction.Transaction;
import pt.diamondcars.dcbobackend.domain.transaction.TransactionType;

/**
 * Builds the {@link Specification} {@link TransactionService#list} uses to combine the optional
 * {@code GET /api/transactions} filters (TASK-011 requirement 1: {@code tipo}, {@code carroId},
 * {@code from}, {@code to}), so any subset can be requested together without one derived-query
 * method per combination — mirrors {@link CarSpecifications}/{@link LeadSpecifications}.
 */
final class TransactionSpecifications {

	private TransactionSpecifications() {}

	/**
	 * Builds a specification that matches every transaction whose {@code tipo}/{@code car_id} column
	 * equals the corresponding non-{@code null} argument, and whose {@code data} falls within
	 * {@code [from, to]} (each bound only applied when non-{@code null}). A {@code null} argument is
	 * not filtered on at all (not "match nothing").
	 *
	 * @param tipo required value of {@code transactions.tipo}, or {@code null} to not filter by it
	 * @param carId required value of {@code transactions.car_id}, or {@code null} to not filter by it
	 * @param from inclusive lower bound of {@code transactions.data}, or {@code null} for no lower
	 *     bound
	 * @param to inclusive upper bound of {@code transactions.data}, or {@code null} for no upper
	 *     bound
	 * @return the combined specification; matches every transaction when every argument is {@code
	 *     null}
	 */
	static Specification<Transaction> matching(TransactionType tipo, UUID carId, LocalDate from, LocalDate to) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (tipo != null) {
				predicates.add(criteriaBuilder.equal(root.get("tipo"), tipo));
			}
			if (carId != null) {
				predicates.add(criteriaBuilder.equal(root.get("car").get("id"), carId));
			}
			if (from != null) {
				predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("data"), from));
			}
			if (to != null) {
				predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("data"), to));
			}
			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		};
	}
}

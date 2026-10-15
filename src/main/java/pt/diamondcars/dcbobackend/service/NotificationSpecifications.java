package pt.diamondcars.dcbobackend.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import pt.diamondcars.dcbobackend.domain.notification.Notification;

/**
 * Builds the {@link Specification} {@link NotificationService#list} uses for the optional {@code
 * GET /api/notifications?read=} filter (TASK-010, requirement 4).
 */
final class NotificationSpecifications {

	private NotificationSpecifications() {}

	/**
	 * Builds a specification that matches every notification whose {@code read} column equals the
	 * given non-{@code null} argument. A {@code null} argument is not filtered on at all.
	 *
	 * @param read required value of {@code notifications.read}, or {@code null} to not filter by it
	 * @return the combined specification; matches every notification when {@code read} is {@code
	 *     null}
	 */
	static Specification<Notification> matching(Boolean read) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (read != null) {
				predicates.add(criteriaBuilder.equal(root.get("read"), read));
			}
			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		};
	}
}

package pt.diamondcars.dcbobackend.domain.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Spring Data repository for {@link Notification}, with the lookups {@code NotificationService}
 * needs (TASK-010): {@link JpaSpecificationExecutor} to combine the optional {@code GET
 * /api/notifications?read=} filter with pagination, and {@link #findByReadFalse()} to back {@code
 * POST /api/notifications/read-all}.
 */
public interface NotificationRepository
		extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

	/**
	 * Lists every unread notification, used by {@code NotificationService#markAllAsRead()} to flip
	 * every one of them to {@code read = true} within a single transaction.
	 *
	 * @return every {@link Notification} with {@code read = false}
	 */
	List<Notification> findByReadFalse();
}

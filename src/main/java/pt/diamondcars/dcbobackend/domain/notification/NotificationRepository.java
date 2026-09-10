package pt.diamondcars.dcbobackend.domain.notification;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Notification}. No derived queries beyond CRUD are required by
 * TASK-006 requirement 7 for this aggregate; endpoint-specific lookups belong to TASK-010.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}

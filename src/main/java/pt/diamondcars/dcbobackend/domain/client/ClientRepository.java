package pt.diamondcars.dcbobackend.domain.client;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Client}. No derived queries beyond CRUD are required by
 * TASK-006 requirement 7 for this aggregate; endpoint-specific lookups belong to TASK-009.
 */
public interface ClientRepository extends JpaRepository<Client, UUID> {
}

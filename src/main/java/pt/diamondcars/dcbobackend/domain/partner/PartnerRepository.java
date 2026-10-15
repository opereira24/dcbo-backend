package pt.diamondcars.dcbobackend.domain.partner;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Partner}. No derived queries beyond CRUD are required by
 * TASK-006 requirement 7 for this aggregate; endpoint-specific lookups belong to TASK-009.
 */
public interface PartnerRepository extends JpaRepository<Partner, UUID> {
}

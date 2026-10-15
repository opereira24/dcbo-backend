package pt.diamondcars.dcbobackend.web.internal;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.diamondcars.dcbobackend.service.LeadService;
import pt.diamondcars.dcbobackend.web.dto.InternalLeadRequest;
import pt.diamondcars.dcbobackend.web.dto.LeadResponse;

/**
 * Entry point for leads submitted by the public site ({@code dc}), relayed by {@code
 * catalog-backend} (TASK-010, requirement 2) — never called directly by the browser.
 *
 * <p>Lives under {@code /internal/**}, a path {@code
 * pt.diamondcars.dcbobackend.config.SecurityConfig} permits without an Auth0 JWT, instead guarded
 * exclusively by {@code pt.diamondcars.dcbobackend.config.InternalTokenFilter} checking the shared
 * {@code CATALOG_SYNC_TOKEN} header — the same token {@code catalog.sync.internal-token} already
 * configures for the opposite direction ({@code dcbo-backend} calling {@code catalog-backend} to
 * propagate car changes). A request with a valid Auth0 JWT but without that header is still
 * rejected with 401: this path never accepts JWT authentication (acceptance criterion 5).
 */
@RestController
@RequestMapping("/internal/leads")
public class InternalLeadController {

	private final LeadService leadService;

	/**
	 * Creates the controller with its backing service.
	 *
	 * @param leadService the service implementing lead creation
	 */
	public InternalLeadController(LeadService leadService) {
		this.leadService = leadService;
	}

	/**
	 * Creates a new lead submitted by the public site, and — within the same transaction — the
	 * notification that always accompanies it.
	 *
	 * @param request the validated payload
	 * @return 201, with a {@code Location} header pointing at the new lead and the created lead as
	 *     the body
	 */
	@PostMapping
	public ResponseEntity<LeadResponse> create(@Valid @RequestBody InternalLeadRequest request) {
		LeadResponse created = leadService.createFromWebsite(request);
		return ResponseEntity.created(URI.create("/api/leads/" + created.id())).body(created);
	}
}

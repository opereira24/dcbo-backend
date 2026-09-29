package pt.diamondcars.dcbobackend.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.diamondcars.dcbobackend.domain.lead.LeadStatus;
import pt.diamondcars.dcbobackend.service.LeadService;
import pt.diamondcars.dcbobackend.web.dto.LeadRequest;
import pt.diamondcars.dcbobackend.web.dto.LeadResponse;

/**
 * REST API for the back-office's lead pipeline (TASK-010), functionally equivalent to the
 * lead-related exports of {@code dcbo/src/services/firebaseService.js} the task requirements list.
 * Every endpoint requires a valid Auth0 JWT (the global rule from {@code
 * pt.diamondcars.dcbobackend.config.SecurityConfig}, TASK-007) — unlike {@code
 * InternalLeadController}, which sits on a separate, unauthenticated-by-JWT path.
 */
@RestController
@RequestMapping("/api/leads")
public class LeadController {

	private final LeadService leadService;

	/**
	 * Creates the controller with its backing service.
	 *
	 * @param leadService the service implementing every operation below
	 */
	public LeadController(LeadService leadService) {
		this.leadService = leadService;
	}

	/**
	 * Lists leads, most recently created first by default, optionally filtered.
	 *
	 * @param status when given, only returns leads with this exact {@code status}
	 * @param carroId when given, only returns leads about this exact car
	 * @param pageable pagination/sorting; defaults to 50 per page, sorted by {@code createdAt}
	 *     descending, when the caller does not ask for something else
	 * @return the requested page of leads, wrapped in a {@link PagedModel}
	 */
	@GetMapping
	public PagedModel<LeadResponse> list(
			@RequestParam(required = false) LeadStatus status,
			@RequestParam(required = false) UUID carroId,
			@PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(leadService.list(status, carroId, pageable));
	}

	/**
	 * Fetches a single lead.
	 *
	 * @param id the lead's id
	 * @return the matching lead (200), or a 404 {@code ApiError} if it does not exist
	 */
	@GetMapping("/{id}")
	public LeadResponse get(@PathVariable UUID id) {
		return leadService.get(id);
	}

	/**
	 * Creates a new lead from the back-office.
	 *
	 * @param request the validated payload
	 * @return 201, with a {@code Location} header pointing at the new lead and the created lead as
	 *     the body
	 */
	@PostMapping
	public ResponseEntity<LeadResponse> create(@Valid @RequestBody LeadRequest request) {
		LeadResponse created = leadService.create(request);
		return ResponseEntity.created(URI.create("/api/leads/" + created.id())).body(created);
	}

	/**
	 * Updates an existing lead's contact data and pipeline state.
	 *
	 * @param id the lead's id
	 * @param request the validated payload
	 * @return the updated lead (200), or a 404 {@code ApiError} if it does not exist
	 */
	@PutMapping("/{id}")
	public LeadResponse update(@PathVariable UUID id, @Valid @RequestBody LeadRequest request) {
		return leadService.update(id, request);
	}

	/**
	 * Deletes a lead.
	 *
	 * @param id the lead's id
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		leadService.delete(id);
	}
}

package pt.diamondcars.dcbobackend.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.diamondcars.dcbobackend.service.PartnerService;
import pt.diamondcars.dcbobackend.web.dto.CarResponse;
import pt.diamondcars.dcbobackend.web.dto.PartnerRequest;
import pt.diamondcars.dcbobackend.web.dto.PartnerResponse;

/**
 * REST API for the consignment partner network (TASK-009), functionally equivalent to the
 * partner-related exports of {@code dcbo/src/services/firebaseService.js} the task requirements
 * list. Every endpoint requires a valid Auth0 JWT (the global rule from {@code
 * pt.diamondcars.dcbobackend.config.SecurityConfig}, TASK-007); no endpoint here is further
 * restricted by role — deleting a partner is guarded instead by {@link
 * PartnerService#delete(UUID)} refusing when consignment cars still reference it (requirement 4).
 */
@RestController
@RequestMapping("/api/partners")
public class PartnerController {

	private final PartnerService partnerService;

	/**
	 * Creates the controller with its backing service.
	 *
	 * @param partnerService the service implementing every operation below
	 */
	public PartnerController(PartnerService partnerService) {
		this.partnerService = partnerService;
	}

	/**
	 * Lists partners, most recently created first by default.
	 *
	 * @param pageable pagination/sorting; defaults to 50 per page, sorted by {@code createdAt}
	 *     descending, when the caller does not ask for something else
	 * @return the requested page of partners, wrapped in a {@link PagedModel}
	 */
	@GetMapping
	public PagedModel<PartnerResponse> list(
			@PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(partnerService.list(pageable));
	}

	/**
	 * Fetches a single partner.
	 *
	 * @param id the partner's id
	 * @return the matching partner (200), or a 404 {@code ApiError} if it does not exist
	 */
	@GetMapping("/{id}")
	public PartnerResponse get(@PathVariable UUID id) {
		return partnerService.get(id);
	}

	/**
	 * Creates a new partner.
	 *
	 * @param request the validated payload
	 * @return 201, with a {@code Location} header pointing at the new partner and the created
	 *     partner as the body
	 */
	@PostMapping
	public ResponseEntity<PartnerResponse> create(@Valid @RequestBody PartnerRequest request) {
		PartnerResponse created = partnerService.create(request);
		return ResponseEntity.created(URI.create("/api/partners/" + created.id())).body(created);
	}

	/**
	 * Updates an existing partner, replacing every field with the given payload.
	 *
	 * @param id the partner's id
	 * @param request the validated payload
	 * @return the updated partner (200), or a 404 {@code ApiError} if it does not exist
	 */
	@PutMapping("/{id}")
	public PartnerResponse update(@PathVariable UUID id, @Valid @RequestBody PartnerRequest request) {
		return partnerService.update(id, request);
	}

	/**
	 * Deletes a partner.
	 *
	 * @param id the partner's id
	 * @throws pt.diamondcars.dcbobackend.web.exception.ResourceInUseException if the partner has
	 *     consignment cars associated (mapped to 409 by {@link ApiExceptionHandler})
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		partnerService.delete(id);
	}

	/**
	 * Lists the consignment cars attributed to a partner, most recently created first.
	 *
	 * @param id the partner's id
	 * @return the matching cars (200), or a 404 {@code ApiError} if the partner does not exist
	 */
	@GetMapping("/{id}/cars")
	public List<CarResponse> cars(@PathVariable UUID id) {
		return partnerService.cars(id);
	}
}

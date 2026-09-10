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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.diamondcars.dcbobackend.service.CarService;
import pt.diamondcars.dcbobackend.web.dto.CarRequest;
import pt.diamondcars.dcbobackend.web.dto.CarResponse;
import pt.diamondcars.dcbobackend.web.dto.HighlightRequest;
import pt.diamondcars.dcbobackend.web.dto.SellCarRequest;

/**
 * REST API for the car inventory (TASK-008), functionally equivalent to the car-related exports
 * of {@code dcbo/src/services/firebaseService.js} the task requirements list. Every endpoint
 * requires a valid Auth0 JWT (the global rule from {@code
 * pt.diamondcars.dcbobackend.config.SecurityConfig}, TASK-007); {@link #delete(UUID)} additionally
 * requires the {@code ADMIN} role — see its Javadoc for why.
 */
@RestController
@RequestMapping("/api/cars")
public class CarController {

	private final CarService carService;

	/**
	 * Creates the controller with its backing service.
	 *
	 * @param carService the service implementing every operation below
	 */
	public CarController(CarService carService) {
		this.carService = carService;
	}

	/**
	 * Lists cars, most recently created first by default, optionally filtered.
	 *
	 * @param vendido when given, only returns cars with this exact {@code vendido} value
	 * @param reservado when given, only returns cars with this exact {@code reservado} value
	 * @param destaque when given, only returns cars with this exact {@code destaque} value
	 * @param pageable pagination/sorting; defaults to 50 per page, sorted by {@code createdAt}
	 *     descending, when the caller does not ask for something else
	 * @return the requested page of cars, wrapped in a {@link PagedModel} for a JSON shape ({@code
	 *     content}/{@code page}) that Spring Data guarantees stable across versions — returning a
	 *     raw {@code Page} instead logs a warning and offers no such guarantee
	 */
	@GetMapping
	public PagedModel<CarResponse> list(
			@RequestParam(required = false) Boolean vendido,
			@RequestParam(required = false) Boolean reservado,
			@RequestParam(required = false) Boolean destaque,
			@PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(carService.list(vendido, reservado, destaque, pageable));
	}

	/**
	 * Fetches a single car.
	 *
	 * @param id the car's id
	 * @return the matching car (200), or a 404 {@code ApiError} if it does not exist
	 */
	@GetMapping("/{id}")
	public CarResponse get(@PathVariable UUID id) {
		return carService.get(id);
	}

	/**
	 * Creates a new car.
	 *
	 * @param request the validated payload
	 * @return 201, with a {@code Location} header pointing at the new car and the created car as
	 *     the body
	 */
	@PostMapping
	public ResponseEntity<CarResponse> create(@Valid @RequestBody CarRequest request) {
		CarResponse created = carService.create(request);
		return ResponseEntity.created(URI.create("/api/cars/" + created.id())).body(created);
	}

	/**
	 * Updates an existing car, replacing every field (including its photos) with the given payload.
	 *
	 * @param id the car's id
	 * @param request the validated payload
	 * @return the updated car (200), or a 404 {@code ApiError} if it does not exist
	 */
	@PutMapping("/{id}")
	public CarResponse update(@PathVariable UUID id, @Valid @RequestBody CarRequest request) {
		return carService.update(id, request);
	}

	/**
	 * Deletes a car and its photos.
	 *
	 * <p>Restricted to {@code ROLE_ADMIN}: unlike every other endpoint in this controller (open to
	 * any authenticated back-office user, matching today's behaviour where {@code dcbo} lets any
	 * logged-in user manage cars), deleting a car is irreversible and destroys financial history
	 * tied to it, so this is the one operation this task chooses to gate by role.
	 *
	 * <p>ASSUNÇÃO: no requirement or acceptance criterion of TASK-008 names a specific
	 * role-restricted endpoint; this choice, and the {@code @PreAuthorize} annotation itself, exist
	 * to satisfy {@code backlog/reviews/TASK-007-r2.md} (nota 3 ao planner), which asks the first
	 * task with method security to add a real "authenticated but wrong role → 403" test, since
	 * {@code @EnableMethodSecurity} (TASK-007) is otherwise never exercised and fails open if
	 * removed by a future refactor.
	 *
	 * @param id the car's id
	 * @throws org.springframework.security.access.AccessDeniedException if the caller lacks {@code
	 *     ROLE_ADMIN} (mapped to 403 by {@link ApiExceptionHandler})
	 */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		carService.delete(id);
	}

	/**
	 * Marks a car as sold.
	 *
	 * @param id the car's id
	 * @param request the sale price and, optionally, the buying client's id
	 * @return the updated car
	 */
	@PostMapping("/{id}/sell")
	public CarResponse sell(@PathVariable UUID id, @Valid @RequestBody SellCarRequest request) {
		return carService.sell(id, request);
	}

	/**
	 * Reverts a car's sale.
	 *
	 * @param id the car's id
	 * @return the updated car
	 */
	@PostMapping("/{id}/revert-sale")
	public CarResponse revertSale(@PathVariable UUID id) {
		return carService.revertSale(id);
	}

	/**
	 * Reserves a car.
	 *
	 * @param id the car's id
	 * @return the updated car
	 */
	@PostMapping("/{id}/reserve")
	public CarResponse reserve(@PathVariable UUID id) {
		return carService.reserve(id);
	}

	/**
	 * Releases a car's reservation.
	 *
	 * @param id the car's id
	 * @return the updated car
	 */
	@PostMapping("/{id}/release-reservation")
	public CarResponse releaseReservation(@PathVariable UUID id) {
		return carService.releaseReservation(id);
	}

	/**
	 * Sets whether a car is featured, enforcing the 8-car limit server-side.
	 *
	 * @param id the car's id
	 * @param request the requested featured state
	 * @return the updated car (200), or a 409 {@code ApiError} if turning it on would exceed the
	 *     limit
	 */
	@PatchMapping("/{id}/highlight")
	public CarResponse highlight(@PathVariable UUID id, @Valid @RequestBody HighlightRequest request) {
		return carService.highlight(id, request);
	}
}

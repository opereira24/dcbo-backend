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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.diamondcars.dcbobackend.service.AppUserService;
import pt.diamondcars.dcbobackend.web.dto.AppUserActiveRequest;
import pt.diamondcars.dcbobackend.web.dto.AppUserRequest;
import pt.diamondcars.dcbobackend.web.dto.AppUserResponse;
import pt.diamondcars.dcbobackend.web.dto.AppUserUpdateRequest;
import pt.diamondcars.dcbobackend.web.exception.ResourceNotFoundException;

/**
 * REST API for back-office user profiles and roles (TASK-012), functionally equivalent to the
 * user-related exports of {@code dcbo/src/services/firebaseService.js} the task requirements list.
 *
 * <p>Every {@code /api/users} endpoint is restricted to {@code ROLE_ADMIN} via {@code
 * @PreAuthorize} (requirement 1/3), unlike most of {@code CarController}/{@code PartnerController}
 * which leave role-gating to the single irreversible operation each already has: managing who can
 * access the back-office at all is admin-only end to end, not just deletion. {@link #me()} is the
 * one exception — it is deliberately open to any authenticated user, since it reports the caller's
 * own profile, not someone else's.
 */
@RestController
public class AppUserController {

	private final AppUserService appUserService;

	/**
	 * Creates the controller with its backing service.
	 *
	 * @param appUserService the service implementing every operation below
	 */
	public AppUserController(AppUserService appUserService) {
		this.appUserService = appUserService;
	}

	/**
	 * Lists back-office user profiles, most recently created first by default.
	 *
	 * @param pageable pagination/sorting; defaults to 50 per page, sorted by {@code createdAt}
	 *     descending, when the caller does not ask for something else
	 * @return the requested page of profiles, wrapped in a {@link PagedModel}
	 * @throws org.springframework.security.access.AccessDeniedException if the caller lacks {@code
	 *     ROLE_ADMIN} (mapped to 403 by {@link ApiExceptionHandler})
	 */
	@GetMapping("/api/users")
	@PreAuthorize("hasRole('ADMIN')")
	public PagedModel<AppUserResponse> list(
			@PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(appUserService.list(pageable));
	}

	/**
	 * Fetches a single back-office user profile.
	 *
	 * @param id the profile's id
	 * @return the matching profile (200), or a 404 {@code ApiError} if it does not exist
	 * @throws org.springframework.security.access.AccessDeniedException if the caller lacks {@code
	 *     ROLE_ADMIN} (mapped to 403 by {@link ApiExceptionHandler})
	 */
	@GetMapping("/api/users/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public AppUserResponse get(@PathVariable UUID id) {
		return appUserService.get(id);
	}

	/**
	 * Registers the local profile for an Auth0 account that already exists in the tenant.
	 *
	 * @param request the validated payload
	 * @return 201, with a {@code Location} header pointing at the new profile and the created
	 *     profile as the body
	 * @throws pt.diamondcars.dcbobackend.web.exception.DuplicateAuthSubjectException if a profile
	 *     already exists for the given {@code authSubject} (mapped to 409)
	 * @throws org.springframework.security.access.AccessDeniedException if the caller lacks {@code
	 *     ROLE_ADMIN} (mapped to 403 by {@link ApiExceptionHandler})
	 */
	@PostMapping("/api/users")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<AppUserResponse> create(@Valid @RequestBody AppUserRequest request) {
		AppUserResponse created = appUserService.create(request);
		return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
	}

	/**
	 * Updates an existing profile's name and role.
	 *
	 * @param id the profile's id
	 * @param request the validated payload
	 * @return the updated profile (200), or a 404 {@code ApiError} if it does not exist
	 * @throws org.springframework.security.access.AccessDeniedException if the caller lacks {@code
	 *     ROLE_ADMIN} (mapped to 403 by {@link ApiExceptionHandler})
	 */
	@PutMapping("/api/users/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public AppUserResponse update(@PathVariable UUID id, @Valid @RequestBody AppUserUpdateRequest request) {
		return appUserService.update(id, request);
	}

	/**
	 * Activates or deactivates a profile.
	 *
	 * @param id the profile's id
	 * @param request the validated payload carrying the new {@code active} state
	 * @return the updated profile (200), or a 404 {@code ApiError} if it does not exist
	 * @throws org.springframework.security.access.AccessDeniedException if the caller lacks {@code
	 *     ROLE_ADMIN} (mapped to 403 by {@link ApiExceptionHandler})
	 */
	@PatchMapping("/api/users/{id}/active")
	@PreAuthorize("hasRole('ADMIN')")
	public AppUserResponse setActive(
			@PathVariable UUID id, @Valid @RequestBody AppUserActiveRequest request) {
		return appUserService.setActive(id, request);
	}

	/**
	 * Deletes a profile.
	 *
	 * @param id the profile's id
	 * @throws pt.diamondcars.dcbobackend.web.exception.SelfDeletionException if {@code id} is the
	 *     authenticated caller's own profile (mapped to 409)
	 * @throws org.springframework.security.access.AccessDeniedException if the caller lacks {@code
	 *     ROLE_ADMIN} (mapped to 403 by {@link ApiExceptionHandler})
	 */
	@DeleteMapping("/api/users/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		appUserService.delete(id);
	}

	/**
	 * Returns the profile of the currently authenticated caller, resolved from the {@code sub} claim
	 * of their JWT. Deliberately not {@code @PreAuthorize}-restricted: every authenticated
	 * back-office user (any role) can read their own profile.
	 *
	 * @return the caller's profile (200)
	 * @throws ResourceNotFoundException if no local profile exists for the caller's {@code sub}
	 *     (mapped to 404) — the frontend treats this as "conta sem acesso atribuido" (TASK-012,
	 *     requirement 1)
	 */
	@GetMapping("/api/me")
	public AppUserResponse me() {
		return appUserService
				.currentProfile()
				.orElseThrow(() -> new ResourceNotFoundException("Sem perfil local para o utilizador autenticado"));
	}
}

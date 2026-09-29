package pt.diamondcars.dcbobackend.service;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.diamondcars.dcbobackend.config.AuthenticatedUserProvider;
import pt.diamondcars.dcbobackend.domain.user.AppUser;
import pt.diamondcars.dcbobackend.domain.user.AppUserRepository;
import pt.diamondcars.dcbobackend.domain.user.AppUserRole;
import pt.diamondcars.dcbobackend.web.dto.AppUserActiveRequest;
import pt.diamondcars.dcbobackend.web.dto.AppUserRequest;
import pt.diamondcars.dcbobackend.web.dto.AppUserResponse;
import pt.diamondcars.dcbobackend.web.dto.AppUserUpdateRequest;
import pt.diamondcars.dcbobackend.web.exception.DuplicateAuthSubjectException;
import pt.diamondcars.dcbobackend.web.exception.ResourceNotFoundException;
import pt.diamondcars.dcbobackend.web.exception.SelfDeletionException;

/**
 * Business logic for the whole {@code /api/users} surface plus {@code GET /api/me} (TASK-012),
 * functionally equivalent to the user-related exports of {@code
 * dcbo/src/services/firebaseService.js} the task requirements list ({@code getUsers}, {@code
 * getUserByAuthId}, {@code createUser}, {@code updateUser}, {@code deactivateUser}/{@code
 * activateUser}, {@code deleteUser}).
 *
 * <p>Every public method is {@code @Transactional} and maps its result to {@link AppUserResponse}
 * before returning, mirroring {@code PartnerService}'s reasoning: with {@code
 * spring.jpa.open-in-view: false}, mapping must happen while the persistence context is still
 * open.
 */
@Service
public class AppUserService {

	private final AppUserRepository appUserRepository;
	private final AuthenticatedUserProvider authenticatedUserProvider;

	/**
	 * Creates the service with its collaborating repository and identity provider.
	 *
	 * @param appUserRepository persistence for {@link AppUser}
	 * @param authenticatedUserProvider resolves the Auth0 {@code sub} of the currently authenticated
	 *     caller, needed by {@link #currentProfile()} and to refuse self-deletion in {@link
	 *     #delete(UUID)} (requirement 1 / acceptance criterion 6)
	 */
	public AppUserService(
			AppUserRepository appUserRepository, AuthenticatedUserProvider authenticatedUserProvider) {
		this.appUserRepository = appUserRepository;
		this.authenticatedUserProvider = authenticatedUserProvider;
	}

	/**
	 * Lists back-office users, most recently created first by default.
	 *
	 * @param pageable pagination/sorting, defaulted by the controller to 50 per page sorted by
	 *     {@code createdAt} descending
	 * @return the requested page, mapped to {@link AppUserResponse}
	 */
	@Transactional(readOnly = true)
	public Page<AppUserResponse> list(Pageable pageable) {
		return appUserRepository.findAll(pageable).map(AppUserResponse::from);
	}

	/**
	 * Fetches a single back-office user profile.
	 *
	 * @param id the profile's id
	 * @return the matching profile
	 * @throws ResourceNotFoundException if no profile has this id (mapped to 404)
	 */
	@Transactional(readOnly = true)
	public AppUserResponse get(UUID id) {
		return AppUserResponse.from(findOrThrow(id));
	}

	/**
	 * Registers the local profile for an Auth0 account that already exists in the tenant (see
	 * {@code backlog/tasks/TASK-012.md}, Notas: this never creates the Auth0 account itself).
	 *
	 * @param request the validated payload
	 * @return the created profile
	 * @throws DuplicateAuthSubjectException if a profile already exists for {@link
	 *     AppUserRequest#authSubject()} (mapped to 409, requirement 1)
	 */
	@Transactional
	public AppUserResponse create(AppUserRequest request) {
		if (appUserRepository.findByAuthSubject(request.authSubject()).isPresent()) {
			throw new DuplicateAuthSubjectException(
					"Ja existe um perfil registado para este authSubject: " + request.authSubject());
		}
		AppUser appUser =
				AppUser.builder()
						.authSubject(request.authSubject())
						.email(request.email())
						.name(request.name())
						.role(AppUserRole.fromValue(request.role()))
						.build();
		return AppUserResponse.from(appUserRepository.save(appUser));
	}

	/**
	 * Updates an existing profile's name and role. Never touches {@link AppUser#getAuthSubject()},
	 * {@link AppUser#getEmail()}, or {@link AppUser#isActive()} — see {@link AppUserUpdateRequest}'s
	 * Javadoc for why.
	 *
	 * @param id the profile's id
	 * @param request the validated payload
	 * @return the updated profile
	 * @throws ResourceNotFoundException if no profile has this id (mapped to 404)
	 */
	@Transactional
	public AppUserResponse update(UUID id, AppUserUpdateRequest request) {
		AppUser appUser = findOrThrow(id);
		appUser.setName(request.name());
		appUser.setRole(AppUserRole.fromValue(request.role()));
		return AppUserResponse.from(appUser);
	}

	/**
	 * Activates or deactivates a profile, the combined equivalent of {@code
	 * deactivateUser}/{@code activateUser} in {@code dcbo/src/services/firebaseService.js:714-737}.
	 *
	 * @param id the profile's id
	 * @param request the validated payload carrying the new {@code active} state
	 * @return the updated profile
	 * @throws ResourceNotFoundException if no profile has this id (mapped to 404)
	 */
	@Transactional
	public AppUserResponse setActive(UUID id, AppUserActiveRequest request) {
		AppUser appUser = findOrThrow(id);
		appUser.setActive(request.active());
		return AppUserResponse.from(appUser);
	}

	/**
	 * Deletes a profile, refusing when the caller is trying to delete their own (requirement 1 /
	 * acceptance criterion 6) — see {@link SelfDeletionException}'s Javadoc for why this is refused
	 * outright rather than allowed.
	 *
	 * @param id the profile's id
	 * @throws ResourceNotFoundException if no profile has this id (mapped to 404)
	 * @throws SelfDeletionException if {@code id} is the authenticated caller's own profile (mapped
	 *     to 409)
	 */
	@Transactional
	public void delete(UUID id) {
		AppUser appUser = findOrThrow(id);
		authenticatedUserProvider
				.getCurrentUserSubject()
				.filter(subject -> subject.equals(appUser.getAuthSubject()))
				.ifPresent(
						subject -> {
							throw new SelfDeletionException("Nao e possivel eliminar o proprio perfil: " + id);
						});
		appUserRepository.delete(appUser);
	}

	/**
	 * Resolves the profile of the currently authenticated caller, for {@code GET /api/me}
	 * (requirement 1).
	 *
	 * @return the matching profile, or {@link Optional#empty()} if there is no authenticated
	 *     subject, or no local profile exists for it yet (the controller maps either case to 404)
	 */
	@Transactional(readOnly = true)
	public Optional<AppUserResponse> currentProfile() {
		return authenticatedUserProvider
				.getCurrentUserSubject()
				.flatMap(appUserRepository::findByAuthSubject)
				.map(AppUserResponse::from);
	}

	private AppUser findOrThrow(UUID id) {
		return appUserRepository
				.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Utilizador nao encontrado: " + id));
	}
}

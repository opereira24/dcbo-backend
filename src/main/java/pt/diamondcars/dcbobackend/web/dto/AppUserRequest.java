package pt.diamondcars.dcbobackend.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /api/users} (TASK-012, requirement 1), registering the local
 * back-office profile of a Auth0 account the user already created manually in the tenant (see
 * {@code backlog/tasks/TASK-012.md}, Notas: this endpoint never creates the Auth0 account itself).
 *
 * <p>Never exposes or accepts an {@code id}: the entity identifier is always server-generated
 * (mirrors {@link CarRequest}). Never accepts {@code active}: a newly registered profile always
 * starts active, matching {@code createUser} in {@code dcbo/src/services/firebaseService.js:687-698}
 * (which hardcodes {@code active: true} regardless of the caller's payload).
 *
 * @param authSubject the Auth0 {@code sub} claim of the account this profile is being registered
 *     for, required, unique — a duplicate value is rejected with 409 by {@link
 *     pt.diamondcars.dcbobackend.service.AppUserService#create}, not by a database-level check
 *     surfacing as 500
 * @param email the user's email address, required and format-checked, matching what {@code
 *     dcbo/src/pages/users.js} collects for a new user
 * @param name full name, required, 2 to 100 characters, matching the range every other {@code
 *     name}-like field in this API enforces ({@link ClientRequest#name}, {@link
 *     PartnerRequest#name})
 * @param role one of {@code USER_ROLES} ({@code dcbo/src/services/userManagementService.js:124-131}),
 *     {@code "admin"} or {@code "user"}; an unrecognised value is rejected here with 400 instead of
 *     reaching {@link pt.diamondcars.dcbobackend.domain.user.AppUserRole#fromValue(String)} and
 *     surfacing as an unmapped {@link IllegalArgumentException} (same pattern as {@link
 *     LeadRequest#status})
 */
public record AppUserRequest(
		@NotBlank @Size(max = 255) String authSubject,
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 2, max = 100) String name,
		@NotBlank @Pattern(regexp = AppUserRequest.ROLE_REGEXP) String role) {

	/**
	 * Every value {@link pt.diamondcars.dcbobackend.domain.user.AppUserRole} accepts ({@code admin},
	 * {@code user}), so an unknown role is rejected here with 400 instead of reaching {@code
	 * fromValue} and surfacing as an unmapped {@link IllegalArgumentException}.
	 */
	static final String ROLE_REGEXP = "^(admin|user)$";
}

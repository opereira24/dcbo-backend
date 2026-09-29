package pt.diamondcars.dcbobackend.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code PUT /api/users/{id}} (TASK-012, requirement 1), updating an existing
 * profile's name and role, equivalent to {@code updateUser} in {@code
 * dcbo/src/services/firebaseService.js:701-711} restricted to the two fields {@code
 * dcbo/src/pages/users.js:85-88} actually submits on edit ({@code name}, {@code role}).
 *
 * <p>Never accepts {@code authSubject} or {@code email}: unlike a client/partner, a back-office
 * user's identity (the Auth0 account it is bound to) is never re-pointed at a different account
 * through this endpoint — that would silently transfer one person's history onto another's
 * profile. Never accepts {@code active}: it has its own dedicated endpoint ({@code
 * PATCH /api/users/{id}/active}, requirement 1), matching the separate {@code
 * deactivateUser}/{@code activateUser} exports of {@code firebaseService.js} rather than folding
 * that toggle into the general update.
 *
 * @param name full name, required, 2 to 100 characters, the same range {@link
 *     AppUserRequest#name} enforces
 * @param role one of {@code USER_ROLES}, {@code "admin"} or {@code "user"}; an unrecognised value
 *     is rejected here with 400 (TASK-012, requirement 2)
 */
public record AppUserUpdateRequest(
		@NotBlank @Size(min = 2, max = 100) String name,
		@NotBlank @Pattern(regexp = AppUserRequest.ROLE_REGEXP) String role) {}

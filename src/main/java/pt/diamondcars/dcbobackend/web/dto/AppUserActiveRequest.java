package pt.diamondcars.dcbobackend.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for {@code PATCH /api/users/{id}/active} (TASK-012, requirement 1), the single
 * combined equivalent of the separate {@code deactivateUser}/{@code activateUser} exports of
 * {@code dcbo/src/services/firebaseService.js:714-737} ({@code active: false} deactivates, {@code
 * active: true} reactivates the same profile).
 *
 * @param active the profile's new {@code active} state; required, so an omitted body field is
 *     rejected with 400 instead of silently defaulting to one state or the other
 */
public record AppUserActiveRequest(@NotNull Boolean active) {}

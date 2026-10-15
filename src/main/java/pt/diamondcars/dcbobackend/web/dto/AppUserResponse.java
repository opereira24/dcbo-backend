package pt.diamondcars.dcbobackend.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import pt.diamondcars.dcbobackend.domain.user.AppUser;

/**
 * Response payload for every {@code /api/users} endpoint and {@code GET /api/me} (TASK-012), the
 * read-side counterpart of {@link AppUserRequest}/{@link AppUserUpdateRequest}. Never the JPA
 * entity itself (mirrors {@link CarResponse}/{@link PartnerResponse}).
 *
 * @param id the profile's identifier
 * @param authSubject the Auth0 {@code sub} claim this profile is bound to
 * @param email the user's email address
 * @param name full name
 * @param role {@code "admin"} or {@code "user"} ({@link
 *     pt.diamondcars.dcbobackend.domain.user.AppUserRole#getValue()}), the same lower-case string
 *     {@code dcbo/src/services/userManagementService.js:124-131} uses
 * @param active whether this profile can currently authenticate against the API ({@link
 *     pt.diamondcars.dcbobackend.config.ActiveUserInterceptor})
 * @param createdAt creation timestamp
 * @param updatedAt last-update timestamp
 */
public record AppUserResponse(
		UUID id,
		String authSubject,
		String email,
		String name,
		String role,
		boolean active,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	/**
	 * Builds the response for a given, fully-loaded {@link AppUser}.
	 *
	 * @param appUser the profile to map, never {@code null}
	 * @return the corresponding response DTO
	 */
	public static AppUserResponse from(AppUser appUser) {
		return new AppUserResponse(
				appUser.getId(),
				appUser.getAuthSubject(),
				appUser.getEmail(),
				appUser.getName(),
				appUser.getRole().getValue(),
				appUser.isActive(),
				appUser.getCreatedAt(),
				appUser.getUpdatedAt());
	}
}

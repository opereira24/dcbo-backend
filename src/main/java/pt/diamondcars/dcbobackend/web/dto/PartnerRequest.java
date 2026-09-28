package pt.diamondcars.dcbobackend.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /api/partners} and {@code PUT /api/partners/{id}}, matching the
 * fields {@code dcbo/src/pages/partners.js} form collects and validates (name required, email/
 * phone optional but format-checked when present, free-text notes).
 *
 * <p>Reuses {@link ClientRequest#PHONE_REGEXP} for {@link #phone} rather than the looser,
 * ad-hoc space-stripping check {@code partners.js:61} performs client-side, for the same rule
 * {@code dcbo/src/utils/validation.js} (`PATTERNS.PHONE_PT`) already applies to clients — one
 * canonical phone format server-side, instead of two slightly different ones.
 *
 * <p>Never exposes or accepts an {@code id}. Never accepts {@code carsCount}/{@code
 * totalCommission}: both are derived server-side from consignment car creation/deletion/sale, never
 * written by the caller (TASK-009, requirement 5).
 *
 * @param name full name, required, 3 to 100 characters ({@code partners.js:53-55})
 * @param email optional, but must be a valid address when present ({@link Email} already treats
 *     {@code null}/blank as valid)
 * @param phone optional, same Portuguese format as {@link ClientRequest#phone} when present
 * @param notes optional, free text, max 1000 chars ({@code LIMITS.TEXT_LONG})
 */
public record PartnerRequest(
		@NotBlank @Size(min = 3, max = 100) String name,
		@Email @Size(max = 255) String email,
		@Pattern(regexp = PartnerRequest.OPTIONAL_PHONE_REGEXP) String phone,
		@Size(max = 1000) String notes) {

	/**
	 * {@link ClientRequest#PHONE_REGEXP} widened to also accept an empty string, since a partner's
	 * phone (unlike a client's) is optional. Kept as a separate literal (not derived from {@link
	 * ClientRequest#PHONE_REGEXP} via a method call, e.g. {@code substring}) because an annotation
	 * element value must be a compile-time constant expression.
	 */
	static final String OPTIONAL_PHONE_REGEXP = "^$|(\\+351\\s?)?[29]\\d{8}$";
}

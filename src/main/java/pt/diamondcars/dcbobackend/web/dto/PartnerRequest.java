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
 * <p>The compact constructor strips every whitespace character from {@link #phone} before the
 * {@link Pattern} constraint runs, matching what {@code partners.js:61}
 * (`formData.phone.replace(/\s/g, '')`) already does before testing its own, looser regex
 * client-side: without this, values the {@code dcbo} form happily accepts today
 * (`"912 345 678"`, `"+351 912 345 678"`) are rejected here with 400 (TASK-009,
 * {@code backlog/reviews/TASK-009-r1.md}, IMPORTANTE 4). ASSUNÇÃO: the normalised (space-free)
 * value is also what gets persisted — simpler and more consistent than validating one string and
 * storing another, and the review's own suggested fix names this as an acceptable option
 * ("Normalizar opcionalmente na escrita").
 *
 * <p>Never exposes or accepts an {@code id}. Never accepts {@code carsCount}/{@code
 * totalCommission}: both are derived server-side from a consignment car's sale and the reversal of
 * that sale, never written by the caller (TASK-009, requirement 5).
 *
 * @param name full name, required, 3 to 100 characters ({@code partners.js:53-55})
 * @param email optional, but must be a valid address when present ({@link Email} already treats
 *     {@code null}/blank as valid)
 * @param phone optional, same Portuguese format as {@link ClientRequest#phone} when present, with
 *     whitespace stripped before validation/storage (see above)
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

	/**
	 * Strips whitespace from {@link #phone} before it is stored and validated (see the class
	 * Javadoc), mirroring {@code partners.js:61}.
	 *
	 * @param name see {@link #name}
	 * @param email see {@link #email}
	 * @param phone see {@link #phone}, normalised below
	 * @param notes see {@link #notes}
	 */
	public PartnerRequest {
		phone = phone == null ? null : phone.replaceAll("\\s+", "");
	}
}

package pt.diamondcars.dcbobackend.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import pt.diamondcars.dcbobackend.domain.partner.Partner;

/**
 * Response payload for every {@code /api/partners} endpoint, the read-side counterpart of {@link
 * PartnerRequest}. Never the JPA entity itself (mirrors {@link CarResponse}).
 *
 * <p>{@link #carsCount} uses the entity's own camelCase name rather than the frontend's legacy
 * Firestore key ({@code totalCars}, {@code dcbo/src/pages/partners.js:152}) — the same naming
 * choice already made for {@link ClientResponse#purchasesCount()} (see its Javadoc): a derived
 * aggregate counter is Camada 1 of ADR-001 ("Contadores e agregados derivados"), not a foreign-key
 * name the frontend's read/write flows depend on pervasively, so it is not covered by ADR-001's
 * "preserve the existing JSON key" rule. {@link #totalCommission} already matches the frontend key
 * of the same name, so no rename applies there.
 *
 * @param id the partner's identifier
 * @param name full name
 * @param email email address, or {@code null}/blank if not given
 * @param phone phone number, or {@code null}/blank if not given
 * @param notes free-text notes, or {@code null}/blank if not given
 * @param carsCount number of consignment cars of this partner sold and not reverted, incremented
 *     when a consignment car is sold ({@code CarService#sell}), decremented when that sale is
 *     reverted ({@code CarService#revertSale}), and moved between partners when a sold
 *     consignment car is re-assigned via {@code PUT} ({@code CarService#update}); never touched
 *     by car creation or deletion ({@code CarService}, TASK-009 requirement 5)
 * @param totalCommission running total commission owed to this partner, incremented by a car's
 *     {@code commissionValue} when it is sold and decremented back when that sale is reverted
 * @param createdAt creation timestamp
 * @param updatedAt last-update timestamp
 */
public record PartnerResponse(
		UUID id,
		String name,
		String email,
		String phone,
		String notes,
		int carsCount,
		BigDecimal totalCommission,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	/**
	 * Builds the response for a given, fully-loaded {@link Partner}.
	 *
	 * @param partner the partner to map, never {@code null}
	 * @return the corresponding response DTO
	 */
	public static PartnerResponse from(Partner partner) {
		return new PartnerResponse(
				partner.getId(),
				partner.getName(),
				partner.getEmail(),
				partner.getPhone(),
				partner.getNotes(),
				partner.getCarsCount(),
				partner.getTotalCommission(),
				partner.getCreatedAt(),
				partner.getUpdatedAt());
	}
}

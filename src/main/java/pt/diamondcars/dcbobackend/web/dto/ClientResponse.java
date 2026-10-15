package pt.diamondcars.dcbobackend.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import pt.diamondcars.dcbobackend.domain.client.Client;

/**
 * Response payload for every {@code /api/clients} endpoint, the read-side counterpart of {@link
 * ClientRequest}. Never the JPA entity itself (mirrors {@link CarResponse}), so the persistence
 * model can evolve without breaking the contract consumers depend on.
 *
 * <p>{@link #purchasesCount} uses the entity's own camelCase name rather than the frontend's
 * legacy Firestore key ({@code carsPurchased}, {@code dcbo/src/services/firebaseService.js:265})
 * — the same choice TASK-006 already made for {@link Client#getPurchasesCount()} itself. Unlike
 * the foreign-key JSON names ADR-001 (Camada 3, {@code backlog/CONVENTIONS.md}) pins to the
 * frontend's existing keys, a derived aggregate counter is Camada 1 ("Contadores e agregados
 * derivados"), i.e. structural, and is read from exactly one place in {@code dcbo}, so the rename
 * TASK-022 needs to do when it builds the HTTP client is a single call site, not a contract this
 * task must preserve.
 *
 * @param id the client's identifier
 * @param name full name
 * @param email email address, or {@code null}/blank if not given
 * @param phone phone number
 * @param nif tax id, or {@code null}/blank if not given
 * @param address postal address, or {@code null}/blank if not given
 * @param postalCode postal code, or {@code null}/blank if not given
 * @param notes free-text notes, or {@code null}/blank if not given
 * @param purchasesCount number of cars this client has purchased, maintained by {@code CarService}
 *     on sale/reversal (TASK-008, requirement 4), never written directly by this API's caller
 * @param createdAt creation timestamp
 * @param updatedAt last-update timestamp
 */
public record ClientResponse(
		UUID id,
		String name,
		String email,
		String phone,
		String nif,
		String address,
		String postalCode,
		String notes,
		int purchasesCount,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	/**
	 * Builds the response for a given, fully-loaded {@link Client}.
	 *
	 * @param client the client to map, never {@code null}
	 * @return the corresponding response DTO
	 */
	public static ClientResponse from(Client client) {
		return new ClientResponse(
				client.getId(),
				client.getName(),
				client.getEmail(),
				client.getPhone(),
				client.getNif(),
				client.getAddress(),
				client.getPostalCode(),
				client.getNotes(),
				client.getPurchasesCount(),
				client.getCreatedAt(),
				client.getUpdatedAt());
	}
}

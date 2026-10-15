package pt.diamondcars.dcbobackend.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import pt.diamondcars.dcbobackend.domain.transaction.Transaction;

/**
 * Response payload for every {@code /api/transactions} endpoint, the read-side counterpart of
 * {@link TransactionRequest}. Never the JPA entity itself (mirrors {@link CarResponse}/{@link
 * LeadResponse}), so the persistence model can evolve without breaking the contract {@code dcbo}
 * consumes.
 *
 * <p>{@link #carroId}/{@link #clienteId} keep their Portuguese/frontend JSON names even though the
 * underlying columns/Java association fields are English ({@code car_id}/{@link
 * Transaction#getCar()}, {@code client_id}/{@link Transaction#getClient()}) — the Camada 3 mapping
 * {@code backlog/CONVENTIONS.md} (ADR-001) fixes: FK columns are English, JSON keys stay what
 * {@code dcbo/src/App.js:230,343} already write. {@link #partnerId} is the one FK whose English
 * column name and JSON key already coincide (ADR-001's table), matching {@link CarResponse}.
 *
 * @param id the transaction's identifier
 * @param tipo the transaction's kind, one of {@code compra}/{@code venda}/{@code receita}/{@code
 *     despesa}
 * @param valor the transaction's amount, always strictly positive
 * @param descricao free-text description
 * @param categoria free-text category tag, or {@code null}
 * @param data the calendar date this transaction is recorded on
 * @param carroId id of the car this transaction is about, or {@code null}
 * @param clienteId id of the client involved (typically the buyer of a sale), or {@code null}
 * @param partnerId id of the consignment partner involved (typically the seller of a commission
 *     payout), or {@code null}
 * @param systemGenerated whether this transaction was created automatically by {@code
 *     CarService#sell} (TASK-011 requirement 3), rather than entered manually
 * @param createdAt creation timestamp
 * @param updatedAt last-update timestamp
 */
public record TransactionResponse(
		UUID id,
		String tipo,
		BigDecimal valor,
		String descricao,
		String categoria,
		LocalDate data,
		UUID carroId,
		UUID clienteId,
		UUID partnerId,
		boolean systemGenerated,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	/**
	 * Builds the response for a given, fully-loaded {@link Transaction}.
	 *
	 * <p>Must only be called while the {@link Transaction}'s persistence context is still open (i.e.
	 * from within the {@code @Transactional} service method that loaded it): {@link
	 * Transaction#getCar()}/{@link Transaction#getClient()}/{@link Transaction#getPartner()} are lazy
	 * associations, and accessing them after the session closes would raise a {@code
	 * LazyInitializationException}.
	 *
	 * @param transaction the transaction to map, never {@code null}
	 * @return the corresponding response DTO
	 */
	public static TransactionResponse from(Transaction transaction) {
		return new TransactionResponse(
				transaction.getId(),
				transaction.getTipo().getValue(),
				transaction.getValor(),
				transaction.getDescricao(),
				transaction.getCategoria(),
				transaction.getData(),
				transaction.getCar() != null ? transaction.getCar().getId() : null,
				transaction.getClient() != null ? transaction.getClient().getId() : null,
				transaction.getPartner() != null ? transaction.getPartner().getId() : null,
				transaction.isSystemGenerated(),
				transaction.getCreatedAt(),
				transaction.getUpdatedAt());
	}
}

package pt.diamondcars.dcbobackend.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import pt.diamondcars.dcbobackend.web.exception.InvalidTransactionDateException;

/**
 * Request payload for {@code POST /api/transactions} and {@code PUT /api/transactions/{id}}
 * (TASK-011, requirements 1-2), covering the 4 real {@code tipo} values {@code dcbo} writes today
 * ({@code compra}, {@code venda}, {@code receita}, {@code despesa} — see {@link
 * pt.diamondcars.dcbobackend.domain.transaction.TransactionType}'s Javadoc), not only the two the
 * task text originally anticipated (TASK-011, Notas).
 *
 * <p>Unlike {@link CarRequest}/{@link ClientRequest}, this single record is <em>not</em> applied
 * identically by both endpoints, mirroring {@link LeadRequest}: {@link
 * pt.diamondcars.dcbobackend.service.TransactionService#update} deliberately ignores {@link
 * #carroId}, since {@code dcbo/src/components/transactions-form.js} — the only manual editor —
 * never sends it, and {@code dcbo/src/pages/finances.js}'s "Editar" button opens that same form for
 * every transaction, including automatically-generated sale ones (no {@code tipo} gating), so
 * always re-resolving {@link #carroId} on update would silently detach a sale transaction from its
 * car the first time it is edited from that form. ASSUNÇÃO: no acceptance criterion exercises
 * updating {@link #carroId}, so this mirrors the closest established precedent ({@link
 * LeadRequest}) rather than treating {@code PUT} as a full replace like {@link CarRequest}/{@link
 * PartnerRequest}.
 *
 * <p>Never exposes {@code clienteId}/{@code partnerId}: neither is named by TASK-011 requirement
 * 2, and the manual form never sends them either — they are only ever set by the automatic
 * sale-transaction hook in {@code CarService#sell} (requirement 3), not through this DTO.
 *
 * @param tipo required, one of the 4 real values {@code dcbo} writes: {@code compra}, {@code
 *     venda}, {@code receita}, {@code despesa}
 * @param valor required, strictly positive — never signed, {@link #tipo} alone determines whether
 *     an amount is money in or out (see {@code TransactionService#summary}) — bounded above by
 *     {@value CarRequest#PRICE_MAX_VALUE} ({@link CarRequest#PRICE_MAX_VALUE}, the same monetary
 *     ceiling {@code preco}/{@code precoVenda}/{@code commissionValue} already enforce, applied
 *     here proactively since {@code transactions.valor} is the same {@code numeric(12,2)} column
 *     type and would otherwise overflow it the same way TASK-008 already fixed for cars)
 * @param descricao required, matches {@code transactions.descricao} (max 500 chars)
 * @param categoria optional free-text tag (max 100 chars), matches {@code transactions.categoria}
 *     — {@code dcbo/src/components/transactions-form.js:126-134} only offers a {@code datalist} of
 *     suggestions, never a closed set, so no {@link Pattern} is applied here
 * @param data required, either a plain calendar date ({@code "YYYY-MM-DD"}, matching {@code
 *     dcbo/src/components/transactions-form.js:11,58}) or a full ISO-8601 instant (matching the
 *     fallback {@code dcbo/src/App.js:231} sends, e.g. {@code "2026-03-31T23:30:00Z"}); resolved by
 *     {@link #resolveData()}, which always truncates an instant to a calendar date in the fixed
 *     zone {@code Europe/Lisbon} (TASK-011 requirement 7) — never the JVM/driver default zone —
 *     before it reaches the {@code DATE} column {@code transactions.data} maps
 * @param carroId id of the {@code Car} this transaction is about, optional; if given, must
 *     reference an existing car or {@code POST /api/transactions} responds 400 (TASK-011
 *     requirement 2 explicitly names 400 here, unlike the 404 precedent {@link CarRequest}
 *     ({@code partnerId})/{@link LeadRequest} ({@code carroId}) set for the same "unknown
 *     referenced id" shape elsewhere in this API); only applied on creation, see the class Javadoc
 */
public record TransactionRequest(
		@NotBlank @Pattern(regexp = TransactionRequest.TIPO_REGEXP) String tipo,
		@NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax(CarRequest.PRICE_MAX_VALUE) BigDecimal valor,
		@NotBlank @Size(max = 500) String descricao,
		@Size(max = 100) String categoria,
		@NotBlank String data,
		UUID carroId) {

	/**
	 * Zone TASK-011 requirement 7 fixes for truncating a full ISO-8601 instant {@link #data} into a
	 * calendar date — always {@code Europe/Lisbon}, never the JVM/driver default zone. Also reused by
	 * {@code TransactionService#createSaleTransaction} so the automatically-generated sale
	 * transaction's {@code data} ({@code LocalDate.now(TRANSACTION_ZONE)}) is derived in the exact
	 * same zone as a manually-submitted instant, not the JVM default.
	 */
	public static final ZoneId TRANSACTION_ZONE = ZoneId.of("Europe/Lisbon");

	/**
	 * Every value {@code dcbo} actually writes for {@code transactions.tipo} (TASK-011, Notas):
	 * {@code compra}, {@code venda}, {@code receita}, {@code despesa} — all 4 constants of {@link
	 * pt.diamondcars.dcbobackend.domain.transaction.TransactionType}, not only the 2 the task text
	 * originally anticipated.
	 */
	static final String TIPO_REGEXP = "^(compra|venda|receita|despesa)$";

	/**
	 * Resolves {@link #data} into the calendar date {@code transactions.data} stores, accepting both
	 * formats {@code dcbo} sends (TASK-011 requirement 7): a plain {@code "YYYY-MM-DD"} is parsed
	 * as-is; a full ISO-8601 instant is truncated to a calendar date in {@link #TRANSACTION_ZONE}
	 * ({@code Europe/Lisbon}), explicitly, never the JVM/driver default zone — this is what keeps a
	 * late-evening UTC instant (e.g. {@code "2026-03-31T23:30:00Z"}, already the next day in Lisbon
	 * once daylight saving time starts) from being stored one calendar day early.
	 *
	 * @return the resolved calendar date
	 * @throws InvalidTransactionDateException if {@link #data} matches neither format (mapped to 400
	 *     by {@link pt.diamondcars.dcbobackend.web.ApiExceptionHandler})
	 */
	public LocalDate resolveData() {
		try {
			return LocalDate.parse(data);
		} catch (DateTimeParseException plainDateFailure) {
			try {
				return Instant.parse(data).atZone(TRANSACTION_ZONE).toLocalDate();
			} catch (DateTimeParseException instantFailure) {
				throw new InvalidTransactionDateException(data);
			}
		}
	}
}

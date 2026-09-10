package pt.diamondcars.dcbobackend.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload for {@code POST /api/cars/{id}/sell}, equivalent to {@code sellCar} in {@code
 * dcbo/src/services/firebaseService.js:148}.
 *
 * @param precoVenda the price the car was actually sold for, required, between 100 and {@value
 *     CarRequest#PRICE_MAX_VALUE} — the same floor and ceiling {@code sellCar} enforces ({@code
 *     firebaseService.js:155-160}, {@code min: 100, max: 10000000}). Sharing {@link
 *     CarRequest#PRICE_MAX_VALUE} with {@code CarRequest.preco}/{@code precoCompra}/{@code
 *     commissionValue} also keeps the value comfortably inside what the {@code preco_venda
 *     numeric(12,2)} column can store, avoiding a {@code numeric field overflow} at flush time
 *     (IMPORTANTE, {@code backlog/reviews/TASK-008-r2.md}: without this ceiling, an overflowing
 *     value surfaced as a 409 "conflito" instead of the 400 validation error a pure input mistake
 *     should produce)
 * @param clienteId id of the {@code Client} that purchased the car, optional (a sale does not
 *     always have a known buyer on record)
 */
public record SellCarRequest(
		@NotNull @DecimalMin("100") @DecimalMax(CarRequest.PRICE_MAX_VALUE) BigDecimal precoVenda,
		UUID clienteId) {}

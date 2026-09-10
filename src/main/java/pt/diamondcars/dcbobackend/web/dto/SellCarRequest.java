package pt.diamondcars.dcbobackend.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload for {@code POST /api/cars/{id}/sell}, equivalent to {@code sellCar} in {@code
 * dcbo/src/services/firebaseService.js:148}.
 *
 * @param precoVenda the price the car was actually sold for, required, at least 100 (same floor
 *     {@code sellCar} enforces, {@code firebaseService.js:163-167})
 * @param clienteId id of the {@code Client} that purchased the car, optional (a sale does not
 *     always have a known buyer on record)
 */
public record SellCarRequest(@NotNull @DecimalMin("100") BigDecimal precoVenda, UUID clienteId) {}

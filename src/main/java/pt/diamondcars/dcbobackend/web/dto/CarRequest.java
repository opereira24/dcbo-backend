package pt.diamondcars.dcbobackend.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import pt.diamondcars.dcbobackend.web.dto.validation.MaxCurrentYearPlusOne;

/**
 * Request payload for {@code POST /api/cars} and {@code PUT /api/cars/{id}}, replicating the
 * validation rules of {@code dcbo/src/utils/validation.js:370-384} ({@code carValidationSchema})
 * so a car can never be created/updated server-side with data the browser form would already have
 * rejected (TASK-008, requirement 3).
 *
 * <p>Never exposes or accepts an {@code id}: the entity identifier always comes from the URL path
 * (TASK-008, requirement 2). {@code images}/{@code imageThumbnails} are parallel arrays, index by
 * index, matching the shape {@code dcbo/src/components/cars-form.js:388-389} already sends —
 * sending this pair replaces the car's photos entirely, keeping the given order as {@code
 * car_images.position} (requirement 6). {@link #isConsignacao} keeps the exact frontend key name
 * (not the more idiomatic {@code consignacao}) because it is part of the JSON contract {@code
 * dcbo/src/services/firebaseService.js:81} already writes.
 *
 * @param marca brand, required, matches {@code cars.marca} (max 100 chars, no special characters,
 *     replicating {@code allowSpecialChars: false} in {@code carValidationSchema.marca})
 * @param modelo model, required, matches {@code cars.modelo} (max 100 chars, no special
 *     characters, replicating {@code carValidationSchema.modelo})
 * @param ano model year, required, between 1950 and next calendar year ({@code validateYear},
 *     {@link MaxCurrentYearPlusOne})
 * @param preco sale price, required, between 100 and {@value #PRICE_MAX_VALUE} ({@code
 *     carValidationSchema.preco} / {@code LIMITS.PRICE_MAX}) — the upper bound also keeps values
 *     within what the {@code numeric(12,2)} column can store, avoiding a database overflow error
 * @param km odometer reading, required, between 0 and 1 000 000 ({@code validateKm})
 * @param cor colour, required (max 50 chars, no special characters, replicating {@code
 *     carValidationSchema.cor})
 * @param combustivel fuel type, required
 * @param transmissao transmission type, required
 * @param origem provenance/origin, required
 * @param descricao free-text description, optional (max 1000 chars)
 * @param precoCompra purchase price, optional, between 0 and {@value #PRICE_MAX_VALUE} (same
 *     {@code LIMITS.PRICE_MAX} bound as {@link #preco})
 * @param dataCompra purchase date, optional
 * @param isConsignacao whether this car is sold on consignment for a {@link #partnerId}
 * @param partnerId id of the consignment {@code Partner}, required by the frontend only when
 *     {@link #isConsignacao} is {@code true} (not enforced server-side, out of scope of this task)
 * @param commissionValue commission owed to the partner on sale, optional, between 0 and
 *     {@value #PRICE_MAX_VALUE}
 * @param garantiaMeses warranty length in months, optional, defaults to 0 when {@code null}
 * @param destaque whether this car should be featured; subject to the same 8-car limit
 *     {@code PATCH /api/cars/{id}/highlight} enforces (requirement 1), so setting it to {@code
 *     true} here on a car that was not already featured can also be rejected with 409
 * @param images ordered list of photo URLs; {@code null} is treated as an empty list
 * @param imageThumbnails ordered list of thumbnail URLs, parallel to {@link #images} by index;
 *     shorter than {@link #images} is allowed (missing entries are stored as {@code null})
 */
public record CarRequest(
		@NotBlank @Size(max = 100) @Pattern(regexp = CarRequest.NO_SPECIAL_CHARS_REGEXP) String marca,
		@NotBlank @Size(max = 100) @Pattern(regexp = CarRequest.NO_SPECIAL_CHARS_REGEXP) String modelo,
		@NotNull @Min(1950) @MaxCurrentYearPlusOne Integer ano,
		@NotNull @DecimalMin("100") @DecimalMax(CarRequest.PRICE_MAX_VALUE) BigDecimal preco,
		@NotNull @Min(0) @Max(1_000_000) Integer km,
		@NotBlank @Size(max = 50) @Pattern(regexp = CarRequest.NO_SPECIAL_CHARS_REGEXP) String cor,
		@NotBlank String combustivel,
		@NotBlank String transmissao,
		@NotBlank String origem,
		@Size(max = 1000) String descricao,
		@DecimalMin("0") @DecimalMax(CarRequest.PRICE_MAX_VALUE) BigDecimal precoCompra,
		LocalDate dataCompra,
		boolean isConsignacao,
		UUID partnerId,
		@DecimalMin("0") @DecimalMax(CarRequest.PRICE_MAX_VALUE) BigDecimal commissionValue,
		@Min(0) Integer garantiaMeses,
		boolean destaque,
		List<String> images,
		List<String> imageThumbnails) {

	/**
	 * Upper bound for {@link #preco}/{@link #precoCompra}/{@link #commissionValue}, matching {@code
	 * LIMITS.PRICE_MAX} ({@code dcbo/src/utils/validation.js:11}) — also comfortably inside the
	 * range a {@code numeric(12,2)} column can store, so this bound doubles as the fix for the
	 * {@code numeric field overflow} 500 a much larger {@code preco} used to cause (IMPORTANTE 1,
	 * {@code backlog/reviews/TASK-008-r1.md}).
	 */
	static final String PRICE_MAX_VALUE = "10000000";

	/**
	 * Character-class regexp equivalent to {@code PATTERNS.NO_SPECIAL_CHARS} ({@code
	 * dcbo/src/utils/validation.js:28}), applied to {@link #marca}/{@link #modelo}/{@link #cor}
	 * (IMPORTANTE 1, {@code backlog/reviews/TASK-008-r1.md}: these values are rendered verbatim by
	 * the public catalogue via TASK-017, so unsanitised markup such as {@code <script>} must never
	 * be accepted here).
	 */
	static final String NO_SPECIAL_CHARS_REGEXP = "^[a-zA-Z0-9À-ÿ\\s\\-.,]+$";
}

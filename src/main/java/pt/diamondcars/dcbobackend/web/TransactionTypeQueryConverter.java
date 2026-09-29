package pt.diamondcars.dcbobackend.web;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import pt.diamondcars.dcbobackend.domain.transaction.TransactionType;

/**
 * Binds the {@code ?tipo=} query parameter of {@code GET /api/transactions} (TASK-011 requirement
 * 1) to {@link TransactionType}, overriding Spring MVC's default enum conversion (which calls
 * {@code TransactionType.valueOf(String)} and therefore only accepts the upper-snake-case Java
 * constant name, e.g. {@code "VENDA"}) with one that accepts the lower-case database/JSON value the
 * frontend actually sends, e.g. {@code "venda"} ({@code dcbo/src/pages/finances.js:52-56}) —
 * mirrors {@link LeadStatusQueryConverter} exactly.
 *
 * <p>Registered as a {@link Component} so Spring Boot's auto-configured {@code
 * WebConversionService} picks it up automatically; a converter registered for this exact {@code
 * (String, TransactionType)} pair takes priority over the generic {@code String}-to-{@code Enum}
 * converter factory Spring MVC would otherwise fall back to.
 *
 * <p>An unrecognised value (e.g. {@code ?tipo=nonsense}) still results in a 400: {@link
 * TransactionType#fromValue(String)} throws {@link IllegalArgumentException}, which Spring MVC
 * wraps as a {@code MethodArgumentTypeMismatchException} — already mapped to 400 by {@link
 * ApiExceptionHandler#handleTypeMismatch}.
 */
@Component
public class TransactionTypeQueryConverter implements Converter<String, TransactionType> {

	@Override
	public TransactionType convert(String source) {
		return TransactionType.fromValue(source);
	}
}

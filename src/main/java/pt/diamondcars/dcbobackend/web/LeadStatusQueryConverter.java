package pt.diamondcars.dcbobackend.web;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import pt.diamondcars.dcbobackend.domain.lead.LeadStatus;

/**
 * Binds the {@code ?status=} query parameter of {@code GET /api/leads} (TASK-010, requirement 1)
 * to {@link LeadStatus}, overriding Spring MVC's default enum conversion (which calls {@code
 * LeadStatus.valueOf(String)} and therefore only accepts the upper-snake-case Java constant name,
 * e.g. {@code "CONTACTADO"}) with one that accepts the lower-snake-case database/JSON value the
 * frontend actually sends, e.g. {@code "contactado"} ({@code dcbo/src/pages/leads.js:86}).
 *
 * <p>Registered as a {@link Component} so Spring Boot's auto-configured {@code
 * WebConversionService} picks it up automatically; a converter registered for this exact {@code
 * (String, LeadStatus)} pair takes priority over the generic {@code String}-to-{@code Enum}
 * converter factory Spring MVC would otherwise fall back to.
 *
 * <p>An unrecognised value (e.g. {@code ?status=nonsense}) still results in a 400: {@link
 * LeadStatus#fromValue(String)} throws {@link IllegalArgumentException}, which Spring MVC wraps as
 * a {@code MethodArgumentTypeMismatchException} — already mapped to 400 by {@link
 * ApiExceptionHandler#handleTypeMismatch}.
 */
@Component
public class LeadStatusQueryConverter implements Converter<String, LeadStatus> {

	@Override
	public LeadStatus convert(String source) {
		return LeadStatus.fromValue(source);
	}
}

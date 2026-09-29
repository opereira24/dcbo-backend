package pt.diamondcars.dcbobackend.domain.lead;

import java.util.Arrays;
import pt.diamondcars.dcbobackend.domain.support.PersistentEnum;

/**
 * Origin of a {@link Lead}. {@code V1__init.sql} does not constrain this column with a {@code
 * CHECK}, so the three values ever written are modeled: {@link #WEBSITE} (car detail page,
 * {@code dc/src/services/firebaseService.js:104}) and {@link #WEBSITE_CONTACTO} (general contact
 * form, {@code dc/src/services/firebaseService.js:131}), submitted by the public site through
 * {@code POST /internal/leads}, plus {@link #BACKOFFICE}, written by {@code POST /api/leads}
 * (TASK-010, requirement 6) — the {@code dcbo} back-office never sends an {@code origem} itself,
 * so without this the column would keep falling back to its {@code 'website'} database default
 * for every lead created in the back-office (closes SUG-9 of {@code
 * backlog/reviews/TASK-005-r2.md}).
 */
public enum LeadOrigin implements PersistentEnum {

	WEBSITE("website"),
	WEBSITE_CONTACTO("website-contacto"),
	BACKOFFICE("backoffice");

	private final String value;

	LeadOrigin(String value) {
		this.value = value;
	}

	@Override
	public String getValue() {
		return value;
	}

	/**
	 * Resolves the constant whose {@link #getValue()} equals the given raw string, the inverse of
	 * {@link #getValue()} — used by {@code LeadService}/{@code InternalLeadController} to map an
	 * incoming DTO's plain string field onto this enum (TASK-010).
	 *
	 * @param value the raw database/JSON value to resolve, e.g. {@code "website-contacto"}
	 * @return the matching constant
	 * @throws IllegalArgumentException if no constant has this value
	 */
	public static LeadOrigin fromValue(String value) {
		return Arrays.stream(values())
				.filter(candidate -> candidate.getValue().equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown lead origin: " + value));
	}
}

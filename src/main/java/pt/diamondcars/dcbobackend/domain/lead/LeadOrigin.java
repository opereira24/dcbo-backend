package pt.diamondcars.dcbobackend.domain.lead;

import pt.diamondcars.dcbobackend.domain.support.PersistentEnum;

/**
 * Origin of a {@link Lead}. {@code V1__init.sql} does not constrain this column with a {@code
 * CHECK}, so only the two values the public site actually writes today are modeled ({@code
 * dc/src/services/firebaseService.js:104,131}): {@link #WEBSITE} (car detail page) and {@link
 * #WEBSITE_CONTACTO} (general contact form).
 *
 * <p>The {@code dcbo} back-office never sets this field when creating a lead, which is why {@code
 * leads.origem} defaults to {@code 'website'} at the database level — a known, deliberately
 * unresolved gap (SUG-9 of {@code backlog/reviews/TASK-005-r2.md}) left for the leads endpoint
 * task (TASK-010) to close, e.g. by adding a {@code BACKOFFICE("backoffice")} constant once that
 * task decides the DTO contract.
 */
public enum LeadOrigin implements PersistentEnum {

	WEBSITE("website"),
	WEBSITE_CONTACTO("website-contacto");

	private final String value;

	LeadOrigin(String value) {
		this.value = value;
	}

	@Override
	public String getValue() {
		return value;
	}
}

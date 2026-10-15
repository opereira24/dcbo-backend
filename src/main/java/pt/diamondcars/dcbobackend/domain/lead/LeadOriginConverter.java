package pt.diamondcars.dcbobackend.domain.lead;

import jakarta.persistence.Converter;
import pt.diamondcars.dcbobackend.domain.support.AbstractPersistentEnumConverter;

/**
 * Persists {@link LeadOrigin} to/from the {@code leads.origem} column.
 */
@Converter(autoApply = true)
public class LeadOriginConverter extends AbstractPersistentEnumConverter<LeadOrigin> {

	/** Creates the converter bound to {@link LeadOrigin}. */
	public LeadOriginConverter() {
		super(LeadOrigin.class);
	}
}

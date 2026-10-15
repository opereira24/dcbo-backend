package pt.diamondcars.dcbobackend.domain.lead;

import java.util.Arrays;
import pt.diamondcars.dcbobackend.domain.support.PersistentEnum;

/**
 * Status of a {@link Lead}, mirroring the {@code CHECK} constraint on {@code leads.status} in
 * {@code V1__init.sql:115-117} — itself derived from the options in {@code
 * dcbo/src/components/lead-form.js:83-91} plus {@code 'ativo'} from {@code
 * dc/src/services/firebaseService.js:103}.
 */
public enum LeadStatus implements PersistentEnum {

	ATIVO("ativo"),
	CONTACTADO("contactado"),
	TEST_DRIVE_MARCADO("test_drive_marcado"),
	TEST_DRIVE_REALIZADO("test_drive_realizado"),
	PROPOSTA_FEITA("proposta_feita"),
	NEGOCIACAO("negociacao"),
	VENDIDO("vendido"),
	DESISTIU("desistiu");

	private final String value;

	LeadStatus(String value) {
		this.value = value;
	}

	@Override
	public String getValue() {
		return value;
	}

	/**
	 * Resolves the constant whose {@link #getValue()} equals the given raw string, the inverse of
	 * {@link #getValue()} — used by {@code LeadService} to map an incoming DTO's plain string field
	 * onto this enum (TASK-010).
	 *
	 * @param value the raw database/JSON value to resolve, e.g. {@code "test_drive_marcado"}
	 * @return the matching constant
	 * @throws IllegalArgumentException if no constant has this value
	 */
	public static LeadStatus fromValue(String value) {
		return Arrays.stream(values())
				.filter(candidate -> candidate.getValue().equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown lead status: " + value));
	}
}

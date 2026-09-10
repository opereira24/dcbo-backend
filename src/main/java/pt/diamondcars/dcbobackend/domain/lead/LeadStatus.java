package pt.diamondcars.dcbobackend.domain.lead;

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
}

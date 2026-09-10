package pt.diamondcars.dcbobackend.domain.transaction;

import pt.diamondcars.dcbobackend.domain.support.PersistentEnum;

/**
 * Kind of a {@link Transaction}. The TASK-005 requirement text only anticipated {@code
 * receita}/{@code despesa}, but the real {@code dcbo} frontend ({@code
 * dcbo/src/App.js:226,273,338,356}, {@code dcbo/src/pages/finances.js:52-56}) also writes {@code
 * compra} (a car purchase) and {@code venda} (a car sale/commission) — both are car-linked
 * specializations that still ultimately move money in or out. All four values actually written
 * today are modeled here (protocol rule 8: task file text is a snapshot taken at write time, the
 * real code is the source of truth when the two disagree).
 */
public enum TransactionType implements PersistentEnum {

	COMPRA("compra"),
	VENDA("venda"),
	RECEITA("receita"),
	DESPESA("despesa");

	private final String value;

	TransactionType(String value) {
		this.value = value;
	}

	@Override
	public String getValue() {
		return value;
	}
}

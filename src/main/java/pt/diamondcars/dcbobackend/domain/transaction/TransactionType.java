package pt.diamondcars.dcbobackend.domain.transaction;

import java.util.Arrays;
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

	/**
	 * Resolves the constant whose {@link #getValue()} equals the given raw string, the inverse of
	 * {@link #getValue()} — used by {@link
	 * pt.diamondcars.dcbobackend.web.TransactionTypeQueryConverter} to map the {@code ?tipo=} query
	 * parameter of {@code GET /api/transactions} onto this enum, and by {@code TransactionService} to
	 * map a {@code TransactionRequest.tipo()} onto this enum (TASK-011), mirroring {@code
	 * LeadStatus#fromValue}.
	 *
	 * @param value the raw database/JSON value to resolve, e.g. {@code "venda"}
	 * @return the matching constant
	 * @throws IllegalArgumentException if no constant has this value
	 */
	public static TransactionType fromValue(String value) {
		return Arrays.stream(values())
				.filter(candidate -> candidate.getValue().equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown transaction type: " + value));
	}
}

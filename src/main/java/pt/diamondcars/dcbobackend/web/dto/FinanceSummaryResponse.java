package pt.diamondcars.dcbobackend.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Response payload for {@code GET /api/finances/summary} (TASK-011 requirement 1), the aggregate
 * {@code dcbo/src/pages/finances.js}'s {@code totalReceitas}/{@code totalDespesas}/{@code saldo}
 * cards compute today by loading every transaction into the browser and summing there — moved
 * server-side (TASK-011, Notas) so it keeps working once {@code GET /api/transactions} is
 * paginated.
 *
 * <p>{@code totalReceitas} sums every {@code receita}- and {@code venda}-typed transaction in the
 * requested period; {@code totalDespesas} sums every {@code despesa}- and {@code compra}-typed one
 * — mirroring the grouping {@code dcbo/src/App.js:196-200}'s {@code calculateBalance} already
 * applies ({@code vendas - compras - despesas + receitas}). Every field is a {@link BigDecimal}
 * rounded with {@link RoundingMode#HALF_UP} to exactly 2 decimal places (TASK-011 requirement 5),
 * never a floating-point type.
 *
 * @param totalReceitas sum of every {@code receita}/{@code venda} transaction's {@code valor} in
 *     the requested period
 * @param totalDespesas sum of every {@code despesa}/{@code compra} transaction's {@code valor} in
 *     the requested period
 * @param saldo {@code totalReceitas - totalDespesas}
 */
public record FinanceSummaryResponse(BigDecimal totalReceitas, BigDecimal totalDespesas, BigDecimal saldo) {

	/**
	 * Builds the summary from the two aggregate sums, computing {@link #saldo} and rounding every
	 * field to exactly 2 decimal places with {@link RoundingMode#HALF_UP} (TASK-011 requirement 5).
	 *
	 * @param totalReceitas sum of every {@code receita}/{@code venda} transaction's {@code valor}
	 * @param totalDespesas sum of every {@code despesa}/{@code compra} transaction's {@code valor}
	 * @return the resulting summary, with {@link #saldo} derived and every field scaled to 2 decimals
	 */
	public static FinanceSummaryResponse of(BigDecimal totalReceitas, BigDecimal totalDespesas) {
		BigDecimal scaledReceitas = totalReceitas.setScale(2, RoundingMode.HALF_UP);
		BigDecimal scaledDespesas = totalDespesas.setScale(2, RoundingMode.HALF_UP);
		BigDecimal saldo = scaledReceitas.subtract(scaledDespesas).setScale(2, RoundingMode.HALF_UP);
		return new FinanceSummaryResponse(scaledReceitas, scaledDespesas, saldo);
	}
}

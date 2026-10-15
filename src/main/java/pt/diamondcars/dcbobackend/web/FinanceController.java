package pt.diamondcars.dcbobackend.web;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.diamondcars.dcbobackend.service.TransactionService;
import pt.diamondcars.dcbobackend.web.dto.FinanceSummaryResponse;

/**
 * REST API for finance aggregates (TASK-011), replacing the totals {@code
 * dcbo/src/pages/finances.js}/{@code dcbo/src/pages/dashboard.js} compute in the browser today by
 * loading every transaction into memory. Requires a valid Auth0 JWT (the global rule from {@code
 * pt.diamondcars.dcbobackend.config.SecurityConfig}, TASK-007).
 */
@RestController
@RequestMapping("/api/finances")
public class FinanceController {

	private final TransactionService transactionService;

	/**
	 * Creates the controller with its backing service.
	 *
	 * @param transactionService the service computing the summary below
	 */
	public FinanceController(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	/**
	 * Computes {@code totalReceitas}/{@code totalDespesas}/{@code saldo} for the requested period.
	 *
	 * @param from when given, only counts transactions dated on or after this date
	 * @param to when given, only counts transactions dated on or before this date
	 * @return the computed summary
	 */
	@GetMapping("/summary")
	public FinanceSummaryResponse summary(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return transactionService.summary(from, to);
	}
}

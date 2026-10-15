package pt.diamondcars.dcbobackend.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.diamondcars.dcbobackend.domain.transaction.TransactionType;
import pt.diamondcars.dcbobackend.service.TransactionService;
import pt.diamondcars.dcbobackend.web.dto.TransactionRequest;
import pt.diamondcars.dcbobackend.web.dto.TransactionResponse;

/**
 * REST API for the back-office's transaction ledger (TASK-011), functionally equivalent to the
 * transaction-related exports of {@code dcbo/src/services/firebaseService.js} the task requirements
 * list. Every endpoint requires a valid Auth0 JWT (the global rule from {@code
 * pt.diamondcars.dcbobackend.config.SecurityConfig}, TASK-007).
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

	private final TransactionService transactionService;

	/**
	 * Creates the controller with its backing service.
	 *
	 * @param transactionService the service implementing every operation below
	 */
	public TransactionController(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	/**
	 * Lists transactions, most recently dated first by default, optionally filtered.
	 *
	 * @param tipo when given, only returns transactions with this exact {@code tipo}
	 * @param carroId when given, only returns transactions about this exact car
	 * @param from when given, excludes transactions dated before this (inclusive lower bound)
	 * @param to when given, excludes transactions dated after this (inclusive upper bound)
	 * @param pageable pagination/sorting; defaults to 50 per page, sorted by {@code data}
	 *     descending, when the caller does not ask for something else
	 * @return the requested page of transactions, wrapped in a {@link PagedModel}
	 */
	@GetMapping
	public PagedModel<TransactionResponse> list(
			@RequestParam(required = false) TransactionType tipo,
			@RequestParam(required = false) UUID carroId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@PageableDefault(size = 50, sort = "data", direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(transactionService.list(tipo, carroId, from, to, pageable));
	}

	/**
	 * Fetches a single transaction.
	 *
	 * @param id the transaction's id
	 * @return the matching transaction (200), or a 404 {@code ApiError} if it does not exist
	 */
	@GetMapping("/{id}")
	public TransactionResponse get(@PathVariable UUID id) {
		return transactionService.get(id);
	}

	/**
	 * Creates a new transaction manually.
	 *
	 * @param request the validated payload
	 * @return 201, with a {@code Location} header pointing at the new transaction and the created
	 *     transaction as the body
	 */
	@PostMapping
	public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
		TransactionResponse created = transactionService.create(request);
		return ResponseEntity.created(URI.create("/api/transactions/" + created.id())).body(created);
	}

	/**
	 * Updates an existing transaction's business fields.
	 *
	 * @param id the transaction's id
	 * @param request the validated payload
	 * @return the updated transaction (200), or a 404 {@code ApiError} if it does not exist
	 */
	@PutMapping("/{id}")
	public TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody TransactionRequest request) {
		return transactionService.update(id, request);
	}

	/**
	 * Deletes a transaction.
	 *
	 * @param id the transaction's id
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		transactionService.delete(id);
	}
}

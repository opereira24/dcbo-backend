package pt.diamondcars.dcbobackend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.diamondcars.dcbobackend.domain.car.Car;
import pt.diamondcars.dcbobackend.domain.car.CarRepository;
import pt.diamondcars.dcbobackend.domain.partner.Partner;
import pt.diamondcars.dcbobackend.domain.transaction.Transaction;
import pt.diamondcars.dcbobackend.domain.transaction.TransactionRepository;
import pt.diamondcars.dcbobackend.domain.transaction.TransactionType;
import pt.diamondcars.dcbobackend.web.dto.FinanceSummaryResponse;
import pt.diamondcars.dcbobackend.web.dto.TransactionRequest;
import pt.diamondcars.dcbobackend.web.dto.TransactionResponse;
import pt.diamondcars.dcbobackend.web.exception.InvalidReferenceException;
import pt.diamondcars.dcbobackend.web.exception.ResourceNotFoundException;

/**
 * Business logic for the whole {@code /api/transactions} and {@code /api/finances} surface
 * (TASK-011), functionally equivalent to the transaction-related exports of {@code
 * dcbo/src/services/firebaseService.js} the requirements list ({@code getTransactions}:351,
 * {@code addTransaction}:380, {@code updateTransaction}:393, {@code deleteTransaction}:406, {@code
 * deleteCarTransactions}:415, {@code deleteSaleTransaction}:427), plus the two hooks {@code
 * CarService} calls to keep automatically-generated sale transactions in sync with a car's
 * sale/reversal/deletion (requirements 3-4).
 *
 * <p>Every public method is {@code @Transactional} and maps its result to a {@link
 * TransactionResponse} before returning, mirroring {@code CarService}'s reasoning: with {@code
 * spring.jpa.open-in-view: false}, mapping must happen while the persistence context is still
 * open.
 */
@Service
public class TransactionService {

	/**
	 * {@link TransactionType} values {@link #summary} counts as money in, mirroring {@code
	 * dcbo/src/App.js:197,200}'s {@code calculateBalance} ({@code vendas + receitas}).
	 */
	private static final Set<TransactionType> RECEITA_LIKE_TYPES =
			Set.of(TransactionType.RECEITA, TransactionType.VENDA);

	/**
	 * {@link TransactionType} values {@link #summary} counts as money out, mirroring {@code
	 * dcbo/src/App.js:198-199}'s {@code calculateBalance} ({@code compras + despesas}).
	 */
	private static final Set<TransactionType> DESPESA_LIKE_TYPES =
			Set.of(TransactionType.DESPESA, TransactionType.COMPRA);

	private final TransactionRepository transactionRepository;
	private final CarRepository carRepository;

	/**
	 * Creates the service with its collaborating repositories.
	 *
	 * @param transactionRepository persistence for {@link Transaction}
	 * @param carRepository persistence for {@link Car}, needed to resolve {@code carroId} into a
	 *     managed association when a transaction is created manually (requirement 2)
	 */
	public TransactionService(TransactionRepository transactionRepository, CarRepository carRepository) {
		this.transactionRepository = transactionRepository;
		this.carRepository = carRepository;
	}

	/**
	 * Lists transactions, most recently dated first by default, optionally filtered by {@code tipo}/
	 * {@code carroId}/{@code from}/{@code to} (requirement 1).
	 *
	 * @param tipo required value of {@code transactions.tipo}, or {@code null} to not filter by it
	 * @param carroId required value of {@code transactions.car_id}, or {@code null} to not filter by
	 *     it
	 * @param from inclusive lower bound of {@code transactions.data}, or {@code null} for no lower
	 *     bound
	 * @param to inclusive upper bound of {@code transactions.data}, or {@code null} for no upper
	 *     bound
	 * @param pageable pagination and sorting, defaulted by the controller to 50 per page sorted by
	 *     {@code data} descending
	 * @return the requested page, mapped to {@link TransactionResponse}
	 */
	@Transactional(readOnly = true)
	public Page<TransactionResponse> list(
			TransactionType tipo, UUID carroId, LocalDate from, LocalDate to, Pageable pageable) {
		return transactionRepository
				.findAll(TransactionSpecifications.matching(tipo, carroId, from, to), pageable)
				.map(TransactionResponse::from);
	}

	/**
	 * Fetches a single transaction.
	 *
	 * @param id the transaction's id
	 * @return the matching transaction
	 * @throws ResourceNotFoundException if no transaction has this id (mapped to 404)
	 */
	@Transactional(readOnly = true)
	public TransactionResponse get(UUID id) {
		return TransactionResponse.from(findOrThrow(id));
	}

	/**
	 * Creates a new transaction manually, equivalent to {@code addTransaction} ({@code
	 * dcbo/src/services/firebaseService.js:380}).
	 *
	 * @param request the validated payload
	 * @return the created transaction
	 * @throws InvalidReferenceException if {@code request.carroId()} is given but no car has that id
	 *     (mapped to 400, requirement 2)
	 */
	@Transactional
	public TransactionResponse create(TransactionRequest request) {
		Transaction transaction = new Transaction();
		transaction.setTipo(TransactionType.fromValue(request.tipo()));
		transaction.setValor(request.valor());
		transaction.setDescricao(request.descricao());
		transaction.setCategoria(request.categoria());
		transaction.setData(request.resolveData());
		transaction.setCar(request.carroId() != null ? requireCar(request.carroId()) : null);
		transaction.setSystemGenerated(false);
		return TransactionResponse.from(transactionRepository.save(transaction));
	}

	/**
	 * Updates an existing transaction's business fields ({@code tipo}/{@code valor}/{@code
	 * descricao}/{@code categoria}/{@code data}), equivalent to {@code updateTransaction} ({@code
	 * dcbo/src/services/firebaseService.js:393}).
	 *
	 * <p>Deliberately never touches {@link Transaction#getCar()}/{@link Transaction#getClient()}/
	 * {@link Transaction#getPartner()}/{@link Transaction#isSystemGenerated()} — see {@link
	 * TransactionRequest}'s class Javadoc for why.
	 *
	 * @param id the transaction's id
	 * @param request the validated payload; {@link TransactionRequest#carroId()} is accepted but
	 *     ignored, see above
	 * @return the updated transaction
	 * @throws ResourceNotFoundException if no transaction has this id (mapped to 404)
	 */
	@Transactional
	public TransactionResponse update(UUID id, TransactionRequest request) {
		Transaction transaction = findOrThrow(id);
		transaction.setTipo(TransactionType.fromValue(request.tipo()));
		transaction.setValor(request.valor());
		transaction.setDescricao(request.descricao());
		transaction.setCategoria(request.categoria());
		transaction.setData(request.resolveData());
		return TransactionResponse.from(transaction);
	}

	/**
	 * Deletes a transaction, equivalent to {@code deleteTransaction} ({@code
	 * dcbo/src/services/firebaseService.js:406}).
	 *
	 * @param id the transaction's id
	 * @throws ResourceNotFoundException if no transaction has this id (mapped to 404)
	 */
	@Transactional
	public void delete(UUID id) {
		Transaction transaction = findOrThrow(id);
		transactionRepository.delete(transaction);
	}

	/**
	 * Earliest date substituted for {@link #summary}'s {@code from} when the caller does not give
	 * one — year 1, squarely inside PostgreSQL's {@code DATE} range, unlike {@link LocalDate#MIN}
	 * (see {@link TransactionRepository#sumValorByTipoInAndDataBetween} for why {@code null} is not
	 * passed through instead).
	 */
	private static final LocalDate EARLIEST_SUMMARY_DATE = LocalDate.of(1, 1, 1);

	/**
	 * Latest date substituted for {@link #summary}'s {@code to} when the caller does not give one —
	 * year 9999, squarely inside PostgreSQL's {@code DATE} range, unlike {@link LocalDate#MAX}.
	 */
	private static final LocalDate LATEST_SUMMARY_DATE = LocalDate.of(9999, 12, 31);

	/**
	 * Computes the finances summary for {@code GET /api/finances/summary} (requirement 1), equivalent
	 * to the totals {@code dcbo/src/pages/finances.js} computes in the browser today.
	 *
	 * @param from inclusive lower bound of {@code transactions.data}, or {@code null} for no lower
	 *     bound
	 * @param to inclusive upper bound of {@code transactions.data}, or {@code null} for no upper
	 *     bound
	 * @return the summary, with every amount rounded to 2 decimals with {@link
	 *     java.math.RoundingMode#HALF_UP} (requirement 5)
	 */
	@Transactional(readOnly = true)
	public FinanceSummaryResponse summary(LocalDate from, LocalDate to) {
		LocalDate effectiveFrom = from != null ? from : EARLIEST_SUMMARY_DATE;
		LocalDate effectiveTo = to != null ? to : LATEST_SUMMARY_DATE;
		BigDecimal totalReceitas =
				transactionRepository.sumValorByTipoInAndDataBetween(RECEITA_LIKE_TYPES, effectiveFrom, effectiveTo);
		BigDecimal totalDespesas =
				transactionRepository.sumValorByTipoInAndDataBetween(DESPESA_LIKE_TYPES, effectiveFrom, effectiveTo);
		return FinanceSummaryResponse.of(totalReceitas, totalDespesas);
	}

	/**
	 * Creates the system-generated {@code venda} transaction a car's sale produces, called by {@code
	 * CarService#sell} within the same {@code @Transactional} method (requirement 3), equivalent to
	 * the transaction {@code sellCar} builds ({@code dcbo/src/App.js:337-364}) — a commission-only
	 * transaction for a consignment sale ({@code partner} given), or the full sale price otherwise
	 * ({@code partner} {@code null}).
	 *
	 * @param car the car that was just sold, with {@link Car#getClient()} already set to the buyer
	 *     (or {@code null})
	 * @param valor the amount to record — the commission for a consignment sale, or the sale price
	 *     otherwise
	 * @param descricao human-readable description, matching {@code sellCar}'s own wording
	 * @param categoria {@code "comissao_venda"} for a consignment sale, {@code "venda_veiculo"}
	 *     otherwise, matching {@code sellCar}
	 * @param partner the consignment partner to credit, or {@code null} for a non-consignment sale
	 */
	@Transactional
	public void createSaleTransaction(Car car, BigDecimal valor, String descricao, String categoria, Partner partner) {
		Transaction transaction = new Transaction();
		transaction.setTipo(TransactionType.VENDA);
		transaction.setValor(valor);
		transaction.setDescricao(descricao);
		transaction.setCategoria(categoria);
		transaction.setData(LocalDate.now(TransactionRequest.TRANSACTION_ZONE));
		transaction.setCar(car);
		transaction.setClient(car.getClient());
		transaction.setPartner(partner);
		transaction.setSystemGenerated(true);
		transactionRepository.save(transaction);
	}

	/**
	 * Deletes every system-generated sale transaction linked to a car, called by {@code
	 * CarService#revertSale} within the same {@code @Transactional} method (requirement 3),
	 * equivalent to {@code deleteSaleTransaction} ({@code
	 * dcbo/src/services/firebaseService.js:427}) — but scoped to {@link
	 * Transaction#isSystemGenerated()} rather than just {@code tipo = 'venda'}, so a manual {@code
	 * venda}-typed transaction referencing the same car (which {@link TransactionRepository}'s
	 * lookup by {@code system_generated} deliberately excludes) is never deleted by a sale reversal
	 * it has nothing to do with.
	 *
	 * @param carId the car whose sale is being reverted
	 */
	@Transactional
	public void deleteSaleTransactions(UUID carId) {
		transactionRepository.deleteAll(transactionRepository.findByCarIdAndSystemGeneratedTrue(carId));
	}

	/**
	 * Deletes every transaction linked to a car, called by {@code CarService#delete} within the same
	 * {@code @Transactional} method (requirement 4), equivalent to {@code deleteCarTransactions}
	 * ({@code dcbo/src/services/firebaseService.js:415}) — unlike {@link #deleteSaleTransactions},
	 * this removes every transaction for the car regardless of {@link
	 * Transaction#isSystemGenerated()}, matching {@code deleteCarTransactions}'s own unconditional
	 * query.
	 *
	 * @param carId the car being deleted
	 */
	@Transactional
	public void deleteAllForCar(UUID carId) {
		transactionRepository.deleteAll(transactionRepository.findByCarId(carId));
	}

	private Transaction findOrThrow(UUID id) {
		return transactionRepository
				.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Transacao nao encontrada: " + id));
	}

	private Car requireCar(UUID carId) {
		return carRepository
				.findById(carId)
				.orElseThrow(() -> new InvalidReferenceException("carroId: carro nao encontrado: " + carId));
	}
}

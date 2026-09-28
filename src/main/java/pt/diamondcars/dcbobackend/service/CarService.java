package pt.diamondcars.dcbobackend.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.diamondcars.dcbobackend.domain.car.Car;
import pt.diamondcars.dcbobackend.domain.car.CarImage;
import pt.diamondcars.dcbobackend.domain.car.CarRepository;
import pt.diamondcars.dcbobackend.domain.client.Client;
import pt.diamondcars.dcbobackend.domain.client.ClientRepository;
import pt.diamondcars.dcbobackend.domain.partner.Partner;
import pt.diamondcars.dcbobackend.domain.partner.PartnerRepository;
import pt.diamondcars.dcbobackend.web.dto.CarRequest;
import pt.diamondcars.dcbobackend.web.dto.CarResponse;
import pt.diamondcars.dcbobackend.web.dto.HighlightRequest;
import pt.diamondcars.dcbobackend.web.dto.SellCarRequest;
import pt.diamondcars.dcbobackend.web.exception.CarAlreadySoldException;
import pt.diamondcars.dcbobackend.web.exception.HighlightLimitExceededException;
import pt.diamondcars.dcbobackend.web.exception.ResourceNotFoundException;

/**
 * Business logic for the whole {@code /api/cars} surface (TASK-008), functionally equivalent to
 * the car-related exports of {@code dcbo/src/services/firebaseService.js} the requirements list.
 *
 * <p>Every public method is {@code @Transactional} and maps its result to a {@link CarResponse}
 * before returning, deliberately never handing the {@link Car} entity itself back to the caller:
 * with {@code spring.jpa.open-in-view: false} (see {@code application.yml:13}), any lazy
 * association ({@link Car#getClient()}, {@link Car#getPartner()}, {@link Car#getImages()}) touched
 * after the method returns would raise a {@code LazyInitializationException}, so the mapping must
 * happen while the persistence context is still open.
 */
@Service
public class CarService {

	/** Maximum number of cars that may be featured ({@code destaque = true}) at the same time,
	 * matching the browser-only limit {@code dcbo/src/pages/highlights.js:18} used to enforce. */
	static final int HIGHLIGHT_LIMIT = 8;

	private final CarRepository carRepository;
	private final ClientRepository clientRepository;
	private final PartnerRepository partnerRepository;
	private final PartnerService partnerService;

	/**
	 * Creates the service with its collaborating repositories.
	 *
	 * @param carRepository persistence for {@link Car}
	 * @param clientRepository persistence for {@link Client}, needed to keep {@code
	 *     purchases_count} in sync on sale/reversal (requirement 4)
	 * @param partnerRepository persistence for the consignment partner a car may reference
	 * @param partnerService keeps a consignment partner's {@code cars_count}/{@code
	 *     total_commission} in sync on a car's sale and the reversal of that sale (TASK-009,
	 *     requirement 5 — see {@code backlog/reviews/TASK-009-r1.md}, IMPORTANTE 3)
	 */
	public CarService(
			CarRepository carRepository,
			ClientRepository clientRepository,
			PartnerRepository partnerRepository,
			PartnerService partnerService) {
		this.carRepository = carRepository;
		this.clientRepository = clientRepository;
		this.partnerRepository = partnerRepository;
		this.partnerService = partnerService;
	}

	/**
	 * Lists cars, most recently created first by default, optionally filtered by {@code vendido}/
	 * {@code reservado}/{@code destaque} (requirement 1).
	 *
	 * @param vendido required value of {@code vendido}, or {@code null} to not filter by it
	 * @param reservado required value of {@code reservado}, or {@code null} to not filter by it
	 * @param destaque required value of {@code destaque}, or {@code null} to not filter by it
	 * @param pageable pagination and sorting, defaulted by the controller to 50 per page sorted by
	 *     {@code createdAt} descending
	 * @return the requested page, mapped to {@link CarResponse}
	 */
	@Transactional(readOnly = true)
	public Page<CarResponse> list(Boolean vendido, Boolean reservado, Boolean destaque, Pageable pageable) {
		return carRepository
				.findAll(CarSpecifications.matching(vendido, reservado, destaque), pageable)
				.map(CarResponse::from);
	}

	/**
	 * Fetches a single car.
	 *
	 * @param id the car's id
	 * @return the matching car
	 * @throws ResourceNotFoundException if no car has this id (mapped to 404)
	 */
	@Transactional(readOnly = true)
	public CarResponse get(UUID id) {
		return CarResponse.from(findOrThrow(id));
	}

	/**
	 * Creates a new car. Never touches {@link Partner#getCarsCount()}/{@link
	 * Partner#getTotalCommission()}, even when the car is a consignment car for a partner: those
	 * counters only change on sale/reversal, see {@link #sell}/{@link #revertSale} (TASK-009,
	 * requirement 5 — {@code backlog/reviews/TASK-009-r1.md}, IMPORTANTE 3).
	 *
	 * @param request the validated payload
	 * @return the created car
	 * @throws HighlightLimitExceededException if {@code request.destaque()} is {@code true} and the
	 *     8-car limit is already reached (mapped to 409)
	 */
	@Transactional
	public CarResponse create(CarRequest request) {
		Car car = new Car();
		applyRequest(car, request);
		Car saved = carRepository.save(car);
		return CarResponse.from(saved);
	}

	/**
	 * Updates an existing car, replacing every field (including its photos) with the given payload.
	 *
	 * <p>When the car is already sold ({@code vendido = true}), the consignment fields ({@code
	 * isConsignacao}/{@code partnerId}/{@code commissionValue}) can still change — {@code dcbo}
	 * shows "Editar" on sold cars too ({@code dcbo/src/pages/cars.js:548}, not gated by {@code
	 * !car.vendido}) — so before applying the new values this reconciles the consignment partner's
	 * {@code cars_count}/{@code total_commission} that the original sale registered: it reverses
	 * them from whichever partner/commission the car had *before* the edit, then re-applies them to
	 * whichever partner/commission it has *after*. Without this, {@link #revertSale} would later
	 * undo the sale using the *current* (possibly different) partner/commission, corrupting one or
	 * both partners' balances (TASK-009, {@code backlog/reviews/TASK-009-r1.md}, IMPORTANTE 2).
	 *
	 * @param id the car's id
	 * @param request the validated payload
	 * @return the updated car
	 * @throws ResourceNotFoundException if no car has this id (mapped to 404)
	 * @throws HighlightLimitExceededException if {@code request.destaque()} is {@code true}, the car
	 *     was not already featured, and the 8-car limit is already reached (mapped to 409)
	 */
	@Transactional
	public CarResponse update(UUID id, CarRequest request) {
		Car car = findOrThrow(id);
		if (!car.isVendido()) {
			applyRequest(car, request);
			return CarResponse.from(car);
		}
		boolean previousConsignacao = car.isConsignacao();
		Partner previousPartner = car.getPartner();
		BigDecimal previousCommission = car.getCommissionValue();
		applyRequest(car, request);
		reconcileConsignmentSaleAfterEdit(previousConsignacao, previousPartner, previousCommission, car);
		return CarResponse.from(car);
	}

	/**
	 * Deletes a car and its photos ({@code car_images} cascades by {@link Car#removeImage}/JPA
	 * cascade). Never touches a consignment partner's {@code cars_count}/{@code total_commission}
	 * (TASK-009, requirement 5 — see {@link #create}).
	 *
	 * @param id the car's id
	 * @throws ResourceNotFoundException if no car has this id (mapped to 404)
	 */
	@Transactional
	public void delete(UUID id) {
		Car car = findOrThrow(id);
		carRepository.delete(car);
	}

	/**
	 * Marks a car as sold, equivalent to {@code sellCar} in {@code
	 * dcbo/src/services/firebaseService.js:148}, and — within the same transaction (requirement 4)
	 * — increments the buying client's {@code purchases_count} when a client is given, plus
	 * (TASK-009, requirement 5) the consignment partner's {@code total_commission} by {@link
	 * Car#getCommissionValue()} and {@code cars_count} by one, when the car is a consignment car —
	 * mirroring {@code dcbo/src/App.js:350-351}, where {@code addPartnerCommission}/{@code
	 * incrementPartnerCars} are called together, only from {@code sellCar} (TASK-009,
	 * {@code backlog/reviews/TASK-009-r1.md}, IMPORTANTE 3).
	 *
	 * <p>Only a car that is not currently marked as sold may be sold: the {@code dcbo} frontend
	 * only ever offers the "sell" action from the list of available cars ({@code
	 * dcbo/src/App.js:323-370} is reachable only from there), so a sale is a one-way transition
	 * from "not sold" to "sold", undone only by {@link #revertSale(UUID)}. Calling this on an
	 * already-sold car is rejected rather than silently re-applied, because re-applying it would
	 * double-count {@code purchases_count} (selling the same car twice to the same client) or
	 * orphan the previous buyer's count forever (selling it again without a {@code clienteId},
	 * which used to null out the client reference {@link #revertSale(UUID)} needs to decrement it).
	 *
	 * @param id the car's id
	 * @param request the sale price and, optionally, the buying client's id
	 * @return the updated car
	 * @throws ResourceNotFoundException if the car, or the given client, does not exist (mapped to
	 *     404)
	 * @throws CarAlreadySoldException if the car is already marked as sold (mapped to 409)
	 */
	@Transactional
	public CarResponse sell(UUID id, SellCarRequest request) {
		Car car = findOrThrow(id);
		if (car.isVendido()) {
			throw new CarAlreadySoldException(id);
		}
		car.setVendido(true);
		car.setPrecoVenda(request.precoVenda());
		car.setDataVenda(OffsetDateTime.now());
		car.setClient(request.clienteId() != null ? incrementAndGetClient(request.clienteId()) : null);
		if (car.isConsignacao() && car.getPartner() != null) {
			partnerService.registerCommission(car.getPartner(), car.getCommissionValue());
			partnerService.incrementCarsCount(car.getPartner());
		}
		return CarResponse.from(car);
	}

	/**
	 * Reverts a sale, equivalent to {@code revertCarSale} in {@code
	 * dcbo/src/services/firebaseService.js:179}, decrementing the previously-associated client's
	 * {@code purchases_count} within the same transaction (requirement 4), plus — ASSUNÇÃO, see
	 * {@link PartnerService#reverseCommission} — the consignment partner's {@code total_commission}
	 * and {@code cars_count} back out by what {@link #sell} added, when the car is a consignment
	 * car.
	 *
	 * <p>A car that is not currently marked as sold is left completely untouched (a total no-op,
	 * still returning 200): {@code backlog/reviews/TASK-008-r2.md}, D3 already established this
	 * endpoint is idempotent when called on a never-sold car, but before this guard the partner
	 * side-effect below ran unconditionally, silently discounting a consignment partner's {@code
	 * total_commission}/{@code cars_count} for a sale that never happened — including a second call
	 * on an already-reverted car, corrupting real money (TASK-009,
	 * {@code backlog/reviews/TASK-009-r1.md}, BLOQUEADOR 1).
	 *
	 * @param id the car's id
	 * @return the updated car, unchanged if it was not marked as sold
	 * @throws ResourceNotFoundException if no car has this id (mapped to 404)
	 */
	@Transactional
	public CarResponse revertSale(UUID id) {
		Car car = findOrThrow(id);
		if (!car.isVendido()) {
			return CarResponse.from(car);
		}
		if (car.getClient() != null) {
			decrementPurchases(car.getClient());
		}
		if (car.isConsignacao() && car.getPartner() != null) {
			partnerService.reverseCommission(car.getPartner(), car.getCommissionValue());
			partnerService.decrementCarsCount(car.getPartner());
		}
		car.setVendido(false);
		car.setPrecoVenda(null);
		car.setDataVenda(null);
		car.setClient(null);
		return CarResponse.from(car);
	}

	/**
	 * Reserves a car, equivalent to {@code reservarCar} in {@code
	 * dcbo/src/services/firebaseService.js:194}.
	 *
	 * @param id the car's id
	 * @return the updated car
	 * @throws ResourceNotFoundException if no car has this id (mapped to 404)
	 */
	@Transactional
	public CarResponse reserve(UUID id) {
		Car car = findOrThrow(id);
		car.setReservado(true);
		return CarResponse.from(car);
	}

	/**
	 * Releases a car's reservation, equivalent to {@code libertarReserva} in {@code
	 * dcbo/src/services/firebaseService.js:204}.
	 *
	 * @param id the car's id
	 * @return the updated car
	 * @throws ResourceNotFoundException if no car has this id (mapped to 404)
	 */
	@Transactional
	public CarResponse releaseReservation(UUID id) {
		Car car = findOrThrow(id);
		car.setReservado(false);
		return CarResponse.from(car);
	}

	/**
	 * Sets whether a car is featured, enforcing the 8-car limit server-side (requirement 1), where
	 * it previously only existed in the browser ({@code dcbo/src/pages/highlights.js:18}).
	 *
	 * @param id the car's id
	 * @param request the requested featured state
	 * @return the updated car
	 * @throws ResourceNotFoundException if no car has this id (mapped to 404)
	 * @throws HighlightLimitExceededException if turning this car's {@code destaque} on would
	 *     exceed the limit (mapped to 409)
	 */
	@Transactional
	public CarResponse highlight(UUID id, HighlightRequest request) {
		Car car = findOrThrow(id);
		boolean requestedDestaque = Boolean.TRUE.equals(request.destaque());
		assertHighlightLimitRespected(car, requestedDestaque);
		car.setDestaque(requestedDestaque);
		return CarResponse.from(car);
	}

	private Car findOrThrow(UUID id) {
		return carRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Carro nao encontrado: " + id));
	}

	private Client incrementAndGetClient(UUID clientId) {
		Client client =
				clientRepository
						.findById(clientId)
						.orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado: " + clientId));
		client.setPurchasesCount(client.getPurchasesCount() + 1);
		return client;
	}

	private void decrementPurchases(Client client) {
		client.setPurchasesCount(Math.max(0, client.getPurchasesCount() - 1));
	}

	/**
	 * Moves a sold car's registered commission/{@code cars_count} from the partner/commission it
	 * had before an edit to the partner/commission it has after, so that a later {@link
	 * #revertSale} — which always reverses using the car's *current* values — undoes exactly what
	 * was actually registered, on whichever partner it was registered on. A no-op on either side
	 * when that side was not (or is no longer) a consignment car with a partner (TASK-009,
	 * {@code backlog/reviews/TASK-009-r1.md}, IMPORTANTE 2).
	 *
	 * @param previousConsignacao whether the car was a consignment car before the edit just applied
	 * @param previousPartner the partner the car referenced before the edit, or {@code null}
	 * @param previousCommission the commission value the car had before the edit, or {@code null}
	 * @param car the car after the edit has already been applied, used to read its new consignment
	 *     state
	 */
	private void reconcileConsignmentSaleAfterEdit(
			boolean previousConsignacao, Partner previousPartner, BigDecimal previousCommission, Car car) {
		if (previousConsignacao && previousPartner != null) {
			partnerService.reverseCommission(previousPartner, previousCommission);
			partnerService.decrementCarsCount(previousPartner);
		}
		if (car.isConsignacao() && car.getPartner() != null) {
			partnerService.registerCommission(car.getPartner(), car.getCommissionValue());
			partnerService.incrementCarsCount(car.getPartner());
		}
	}

	private void applyRequest(Car car, CarRequest request) {
		assertHighlightLimitRespected(car, request.destaque());
		car.setMarca(request.marca());
		car.setModelo(request.modelo());
		car.setAno(request.ano());
		car.setPreco(request.preco());
		car.setKm(request.km());
		car.setCor(request.cor());
		car.setCombustivel(request.combustivel());
		car.setTransmissao(request.transmissao());
		car.setOrigem(request.origem());
		car.setDescricao(request.descricao());
		car.setPrecoCompra(request.precoCompra());
		car.setDataCompra(request.dataCompra());
		car.setConsignacao(request.isConsignacao());
		car.setCommissionValue(request.commissionValue());
		car.setGarantiaMeses(request.garantiaMeses() != null ? request.garantiaMeses() : 0);
		car.setDestaque(request.destaque());
		car.setPartner(request.partnerId() != null ? requirePartner(request.partnerId()) : null);
		applyImages(car, request.images(), request.imageThumbnails());
	}

	/**
	 * Fetches a partner by id, failing fast instead of returning an uninitialised proxy.
	 *
	 * <p>Uses {@link PartnerRepository#findById(Object)} rather than {@code getReferenceById},
	 * because a reference proxy for a non-existent id defers the failure to the next flush, where
	 * it surfaces as an unmapped {@code DataIntegrityViolationException} (foreign key violation,
	 * mapped by {@link pt.diamondcars.dcbobackend.web.ApiExceptionHandler} to a generic 409) rather
	 * than the clean 404 a caller-supplied id deserves — the same failure mode already avoided ten
	 * lines above for {@code clienteId} via {@link #incrementAndGetClient(UUID)}.
	 *
	 * @param partnerId the partner's id
	 * @return the matching partner
	 * @throws ResourceNotFoundException if no partner has this id (mapped to 404)
	 */
	private Partner requirePartner(UUID partnerId) {
		return partnerRepository
				.findById(partnerId)
				.orElseThrow(() -> new ResourceNotFoundException("Parceiro nao encontrado: " + partnerId));
	}

	private void assertHighlightLimitRespected(Car car, boolean requestedDestaque) {
		boolean alreadyFeatured = car != null && car.isDestaque();
		if (requestedDestaque && !alreadyFeatured && carRepository.countByDestaqueTrue() >= HIGHLIGHT_LIMIT) {
			throw new HighlightLimitExceededException(HIGHLIGHT_LIMIT);
		}
	}

	private void applyImages(Car car, List<String> urls, List<String> thumbnails) {
		List<String> effectiveUrls = urls != null ? urls : List.of();
		List<String> effectiveThumbnails = thumbnails != null ? thumbnails : List.of();
		for (CarImage existing : new ArrayList<>(car.getImages())) {
			car.removeImage(existing);
		}
		for (int position = 0; position < effectiveUrls.size(); position++) {
			CarImage image =
					CarImage.builder()
							.url(effectiveUrls.get(position))
							.thumbnailUrl(position < effectiveThumbnails.size() ? effectiveThumbnails.get(position) : null)
							.position(position)
							.build();
			car.addImage(image);
		}
	}
}

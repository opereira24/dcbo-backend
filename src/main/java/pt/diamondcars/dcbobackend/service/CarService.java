package pt.diamondcars.dcbobackend.service;

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
import pt.diamondcars.dcbobackend.domain.partner.PartnerRepository;
import pt.diamondcars.dcbobackend.web.dto.CarRequest;
import pt.diamondcars.dcbobackend.web.dto.CarResponse;
import pt.diamondcars.dcbobackend.web.dto.HighlightRequest;
import pt.diamondcars.dcbobackend.web.dto.SellCarRequest;
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

	/**
	 * Creates the service with its collaborating repositories.
	 *
	 * @param carRepository persistence for {@link Car}
	 * @param clientRepository persistence for {@link Client}, needed to keep {@code
	 *     purchases_count} in sync on sale/reversal (requirement 4)
	 * @param partnerRepository persistence for the consignment partner a car may reference
	 */
	public CarService(
			CarRepository carRepository,
			ClientRepository clientRepository,
			PartnerRepository partnerRepository) {
		this.carRepository = carRepository;
		this.clientRepository = clientRepository;
		this.partnerRepository = partnerRepository;
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
	 * Creates a new car.
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
		return CarResponse.from(carRepository.save(car));
	}

	/**
	 * Updates an existing car, replacing every field (including its photos) with the given payload.
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
		applyRequest(car, request);
		return CarResponse.from(car);
	}

	/**
	 * Deletes a car and its photos ({@code car_images} cascades by {@link Car#removeImage}/JPA
	 * cascade).
	 *
	 * @param id the car's id
	 * @throws ResourceNotFoundException if no car has this id (mapped to 404)
	 */
	@Transactional
	public void delete(UUID id) {
		carRepository.delete(findOrThrow(id));
	}

	/**
	 * Marks a car as sold, equivalent to {@code sellCar} in {@code
	 * dcbo/src/services/firebaseService.js:148}, and — within the same transaction (requirement 4)
	 * — increments the buying client's {@code purchases_count} when a client is given.
	 *
	 * @param id the car's id
	 * @param request the sale price and, optionally, the buying client's id
	 * @return the updated car
	 * @throws ResourceNotFoundException if the car, or the given client, does not exist (mapped to
	 *     404)
	 */
	@Transactional
	public CarResponse sell(UUID id, SellCarRequest request) {
		Car car = findOrThrow(id);
		car.setVendido(true);
		car.setPrecoVenda(request.precoVenda());
		car.setDataVenda(OffsetDateTime.now());
		car.setClient(request.clienteId() != null ? incrementAndGetClient(request.clienteId()) : null);
		return CarResponse.from(car);
	}

	/**
	 * Reverts a sale, equivalent to {@code revertCarSale} in {@code
	 * dcbo/src/services/firebaseService.js:179}, decrementing the previously-associated client's
	 * {@code purchases_count} within the same transaction (requirement 4).
	 *
	 * @param id the car's id
	 * @return the updated car
	 * @throws ResourceNotFoundException if no car has this id (mapped to 404)
	 */
	@Transactional
	public CarResponse revertSale(UUID id) {
		Car car = findOrThrow(id);
		if (car.getClient() != null) {
			decrementPurchases(car.getClient());
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
		car.setPartner(request.partnerId() != null ? partnerRepository.getReferenceById(request.partnerId()) : null);
		applyImages(car, request.images(), request.imageThumbnails());
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

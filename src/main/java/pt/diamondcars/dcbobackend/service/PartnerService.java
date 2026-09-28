package pt.diamondcars.dcbobackend.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.diamondcars.dcbobackend.domain.car.CarRepository;
import pt.diamondcars.dcbobackend.domain.partner.Partner;
import pt.diamondcars.dcbobackend.domain.partner.PartnerRepository;
import pt.diamondcars.dcbobackend.web.dto.CarResponse;
import pt.diamondcars.dcbobackend.web.dto.PartnerRequest;
import pt.diamondcars.dcbobackend.web.dto.PartnerResponse;
import pt.diamondcars.dcbobackend.web.exception.ResourceInUseException;
import pt.diamondcars.dcbobackend.web.exception.ResourceNotFoundException;

/**
 * Business logic for the whole {@code /api/partners} surface (TASK-009), functionally equivalent
 * to the partner-related exports of {@code dcbo/src/services/firebaseService.js} the requirements
 * list, plus the {@link #incrementCarsCount}/{@link #decrementCarsCount}/{@link #registerCommission}/
 * {@link #reverseCommission} hooks {@code CarService} calls to keep {@link Partner#getCarsCount()}/
 * {@link Partner#getTotalCommission()} in sync server-side (requirement 5), replacing the ad-hoc
 * increments the browser used to perform on its own.
 *
 * <p>Both counters are only ever touched by a sale and its reversal ({@code CarService#sell}/
 * {@code CarService#revertSale}), never by a car's creation, update, or deletion: {@code
 * dcbo/src/App.js:350-351} only ever calls {@code incrementPartnerCars}/{@code
 * addPartnerCommission} together, from inside {@code sellCar} — see {@code
 * backlog/reviews/TASK-009-r1.md}, IMPORTANTE 3.
 *
 * <p>Every public method is {@code @Transactional} and maps its result to a {@link
 * PartnerResponse}/{@link CarResponse} before returning, mirroring {@code CarService}'s reasoning:
 * with {@code spring.jpa.open-in-view: false}, mapping must happen while the persistence context
 * is still open.
 */
@Service
public class PartnerService {

	private final PartnerRepository partnerRepository;
	private final CarRepository carRepository;

	/**
	 * Creates the service with its collaborating repositories.
	 *
	 * @param partnerRepository persistence for {@link Partner}
	 * @param carRepository persistence for {@link pt.diamondcars.dcbobackend.domain.car.Car},
	 *     needed to list a partner's consignment cars and to refuse deleting a partner that still
	 *     has some (requirement 4)
	 */
	public PartnerService(PartnerRepository partnerRepository, CarRepository carRepository) {
		this.partnerRepository = partnerRepository;
		this.carRepository = carRepository;
	}

	/**
	 * Lists partners, most recently created first by default.
	 *
	 * @param pageable pagination/sorting, defaulted by the controller to 50 per page sorted by
	 *     {@code createdAt} descending
	 * @return the requested page, mapped to {@link PartnerResponse}
	 */
	@Transactional(readOnly = true)
	public Page<PartnerResponse> list(Pageable pageable) {
		return partnerRepository.findAll(pageable).map(PartnerResponse::from);
	}

	/**
	 * Fetches a single partner.
	 *
	 * @param id the partner's id
	 * @return the matching partner
	 * @throws ResourceNotFoundException if no partner has this id (mapped to 404)
	 */
	@Transactional(readOnly = true)
	public PartnerResponse get(UUID id) {
		return PartnerResponse.from(findOrThrow(id));
	}

	/**
	 * Creates a new partner.
	 *
	 * @param request the validated payload
	 * @return the created partner
	 */
	@Transactional
	public PartnerResponse create(PartnerRequest request) {
		Partner partner = new Partner();
		applyRequest(partner, request);
		return PartnerResponse.from(partnerRepository.save(partner));
	}

	/**
	 * Updates an existing partner, replacing every field with the given payload. Never touches
	 * {@link Partner#getCarsCount()}/{@link Partner#getTotalCommission()}, which are only ever
	 * changed by the hooks below (requirement 5).
	 *
	 * @param id the partner's id
	 * @param request the validated payload
	 * @return the updated partner
	 * @throws ResourceNotFoundException if no partner has this id (mapped to 404)
	 */
	@Transactional
	public PartnerResponse update(UUID id, PartnerRequest request) {
		Partner partner = findOrThrow(id);
		applyRequest(partner, request);
		return PartnerResponse.from(partner);
	}

	/**
	 * Deletes a partner, refusing when it still has consignment cars associated (requirement 4)
	 * rather than deleting it silently and orphaning those cars' {@code partner_id}.
	 *
	 * @param id the partner's id
	 * @throws ResourceNotFoundException if no partner has this id (mapped to 404)
	 * @throws ResourceInUseException if at least one car still references this partner (mapped to
	 *     409)
	 */
	@Transactional
	public void delete(UUID id) {
		Partner partner = findOrThrow(id);
		if (carRepository.existsByPartnerId(id)) {
			throw new ResourceInUseException("Parceiro tem carros de consignacao associados: " + id);
		}
		partnerRepository.delete(partner);
	}

	/**
	 * Lists the consignment cars attributed to a partner, most recently created first.
	 *
	 * @param id the partner's id
	 * @return the matching cars, mapped to {@link CarResponse}
	 * @throws ResourceNotFoundException if no partner has this id (mapped to 404)
	 */
	@Transactional(readOnly = true)
	public List<CarResponse> cars(UUID id) {
		findOrThrow(id);
		return carRepository.findByPartnerIdOrderByCreatedAtDesc(id).stream().map(CarResponse::from).toList();
	}

	/**
	 * Increments {@link Partner#getCarsCount()} by one, called by {@code CarService} when a
	 * consignment car referencing this partner is sold (requirement 5) — mirroring {@code
	 * dcbo/src/App.js:351}, the only place the browser calls {@code incrementPartnerCars}, always
	 * right after {@code addPartnerCommission} inside {@code sellCar}.
	 *
	 * @param partner the partner to update, managed by the caller's persistence context; the
	 *     mutation is flushed by Hibernate's dirty checking at commit, no explicit save needed
	 */
	@Transactional
	public void incrementCarsCount(Partner partner) {
		partner.setCarsCount(partner.getCarsCount() + 1);
	}

	/**
	 * Decrements {@link Partner#getCarsCount()} by one (never below zero), called by {@code
	 * CarService} when a consignment car's sale is reverted (the counterpart of {@link
	 * #incrementCarsCount}), or when an already-sold consignment car is re-assigned to a different
	 * partner via {@code PUT /api/cars/{id}} (see {@code CarService#update}).
	 *
	 * @param partner the partner to update, managed by the caller's persistence context
	 */
	@Transactional
	public void decrementCarsCount(Partner partner) {
		partner.setCarsCount(Math.max(0, partner.getCarsCount() - 1));
	}

	/**
	 * Adds a car's commission to {@link Partner#getTotalCommission()}, called by {@code CarService}
	 * when a consignment car referencing this partner is sold (requirement 5).
	 *
	 * @param partner the partner to update, managed by the caller's persistence context
	 * @param commission the commission owed on this sale; the call is a no-op if {@code null}
	 */
	@Transactional
	public void registerCommission(Partner partner, BigDecimal commission) {
		if (commission == null) {
			return;
		}
		partner.setTotalCommission(partner.getTotalCommission().add(commission));
	}

	/**
	 * Subtracts a car's commission back out of {@link Partner#getTotalCommission()} (never below
	 * zero), called by {@code CarService} when a consignment car's sale is reverted.
	 *
	 * <p>ASSUNÇÃO: not explicitly named as a trigger by requirement 5 (which lists "criado, apagado
	 * ou vendido"), added for the same data-integrity reason the task's own Notas already gives for
	 * deriving these counters server-side in the first place: leaving a reverted sale's commission
	 * permanently on the partner's balance would make {@code total_commission} diverge from the
	 * cars actually sold, with no way to correct it afterwards — the same failure mode {@code
	 * CarAlreadySoldException} already exists to prevent for {@code purchases_count}.
	 *
	 * @param partner the partner to update, managed by the caller's persistence context
	 * @param commission the commission to reverse; the call is a no-op if {@code null}
	 */
	@Transactional
	public void reverseCommission(Partner partner, BigDecimal commission) {
		if (commission == null) {
			return;
		}
		BigDecimal reversed = partner.getTotalCommission().subtract(commission);
		partner.setTotalCommission(reversed.max(BigDecimal.ZERO));
	}

	private Partner findOrThrow(UUID id) {
		return partnerRepository
				.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Parceiro nao encontrado: " + id));
	}

	private void applyRequest(Partner partner, PartnerRequest request) {
		partner.setName(request.name());
		partner.setEmail(request.email());
		partner.setPhone(request.phone());
		partner.setNotes(request.notes());
	}
}

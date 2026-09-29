package pt.diamondcars.dcbobackend.service;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.diamondcars.dcbobackend.domain.car.Car;
import pt.diamondcars.dcbobackend.domain.car.CarRepository;
import pt.diamondcars.dcbobackend.domain.lead.Lead;
import pt.diamondcars.dcbobackend.domain.lead.LeadOrigin;
import pt.diamondcars.dcbobackend.domain.lead.LeadRepository;
import pt.diamondcars.dcbobackend.domain.lead.LeadStatus;
import pt.diamondcars.dcbobackend.web.dto.InternalLeadRequest;
import pt.diamondcars.dcbobackend.web.dto.LeadRequest;
import pt.diamondcars.dcbobackend.web.dto.LeadResponse;
import pt.diamondcars.dcbobackend.web.exception.ResourceNotFoundException;

/**
 * Business logic for the whole {@code /api/leads} surface plus the website submission entry point
 * {@code POST /internal/leads} (TASK-010), functionally equivalent to the lead-related exports of
 * {@code dcbo/src/services/firebaseService.js} the requirements list ({@code getLeads}:447, {@code
 * getCarLeads}:460, {@code addLead}:490, {@code updateLead}:525, {@code deleteLead}:558).
 *
 * <p>Every public method is {@code @Transactional} and maps its result to a {@link LeadResponse}
 * before returning, mirroring {@code CarService}'s reasoning: with {@code spring.jpa.open-in-view:
 * false}, mapping must happen while the persistence context is still open.
 */
@Service
public class LeadService {

	private final LeadRepository leadRepository;
	private final CarRepository carRepository;
	private final NotificationService notificationService;

	/**
	 * Creates the service with its collaborating repositories/services.
	 *
	 * @param leadRepository persistence for {@link Lead}
	 * @param carRepository persistence for {@link Car}, needed to resolve {@code carroId} into a
	 *     managed association when a lead is created
	 * @param notificationService used to raise the notification that always accompanies a new
	 *     website lead (requirement 3)
	 */
	public LeadService(LeadRepository leadRepository, CarRepository carRepository, NotificationService notificationService) {
		this.leadRepository = leadRepository;
		this.carRepository = carRepository;
		this.notificationService = notificationService;
	}

	/**
	 * Lists leads, most recently created first by default, optionally filtered by {@code status}/
	 * {@code carroId} (requirement 1).
	 *
	 * @param status required value of {@code leads.status}, or {@code null} to not filter by it
	 * @param carroId required value of {@code leads.car_id}, or {@code null} to not filter by it
	 * @param pageable pagination/sorting, defaulted by the controller to 50 per page sorted by
	 *     {@code createdAt} descending
	 * @return the requested page, mapped to {@link LeadResponse}
	 */
	@Transactional(readOnly = true)
	public Page<LeadResponse> list(LeadStatus status, UUID carroId, Pageable pageable) {
		return leadRepository.findAll(LeadSpecifications.matching(status, carroId), pageable).map(LeadResponse::from);
	}

	/**
	 * Fetches a single lead.
	 *
	 * @param id the lead's id
	 * @return the matching lead
	 * @throws ResourceNotFoundException if no lead has this id (mapped to 404)
	 */
	@Transactional(readOnly = true)
	public LeadResponse get(UUID id) {
		return LeadResponse.from(findOrThrow(id));
	}

	/**
	 * Creates a new lead from the back-office ({@code POST /api/leads}), equivalent to {@code
	 * addLead} ({@code dcbo/src/services/firebaseService.js:490}).
	 *
	 * <p>Always stores {@link LeadOrigin#BACKOFFICE} (requirement 6), never the {@code 'website'}
	 * database default {@code leads.origem} would otherwise fall back to, since {@code
	 * dcbo/src/components/lead-form.js} never sends an {@code origem} of its own.
	 *
	 * @param request the validated payload
	 * @return the created lead
	 * @throws ResourceNotFoundException if {@code request.carroId()} is given but no car has that id
	 *     (mapped to 404)
	 */
	@Transactional
	public LeadResponse create(LeadRequest request) {
		Lead lead = new Lead();
		applyContactFields(lead, request);
		lead.setCar(request.carroId() != null ? requireCar(request.carroId()) : null);
		lead.setCarroMarca(request.carroMarca());
		lead.setCarroModelo(request.carroModelo());
		lead.setCarroPreco(request.carroPreco());
		lead.setOrigem(LeadOrigin.BACKOFFICE);
		return LeadResponse.from(leadRepository.save(lead));
	}

	/**
	 * Updates an existing lead ({@code PUT /api/leads/{id}}), equivalent to {@code updateLead}
	 * ({@code dcbo/src/services/firebaseService.js:525-545}).
	 *
	 * <p>Deliberately never touches {@link Lead#getCar()}/{@link Lead#getCarroMarca()}/{@link
	 * Lead#getCarroModelo()}/{@link Lead#getCarroPreco()}, nor {@link Lead#getOrigem()}: {@code
	 * updateLead} only ever writes {@code nome}/{@code telefone}/{@code email}/{@code notas}/{@code
	 * status}/{@code followUpDate} (lines 540-545), never the car snapshot or the origin a lead was
	 * created with — a lead's car association and provenance are fixed at creation time, only its
	 * contact data and pipeline state change afterwards.
	 *
	 * @param id the lead's id
	 * @param request the validated payload; {@link LeadRequest#carroId()}/{@link
	 *     LeadRequest#carroMarca()}/{@link LeadRequest#carroModelo()}/{@link
	 *     LeadRequest#carroPreco()} are accepted but ignored, see above
	 * @return the updated lead
	 * @throws ResourceNotFoundException if no lead has this id (mapped to 404)
	 */
	@Transactional
	public LeadResponse update(UUID id, LeadRequest request) {
		Lead lead = findOrThrow(id);
		applyContactFields(lead, request);
		return LeadResponse.from(lead);
	}

	/**
	 * Deletes a lead.
	 *
	 * @param id the lead's id
	 * @throws ResourceNotFoundException if no lead has this id (mapped to 404)
	 */
	@Transactional
	public void delete(UUID id) {
		Lead lead = findOrThrow(id);
		leadRepository.delete(lead);
	}

	/**
	 * Creates a new lead submitted by the public site, relayed by {@code catalog-backend} through
	 * {@code POST /internal/leads} (requirement 2), and — within the same transaction (requirement
	 * 3) — the notification that always accompanies it.
	 *
	 * <p>Always stores {@link LeadStatus#ATIVO}, matching {@code createLead}/{@code
	 * createGeneralContact} ({@code dc/src/services/firebaseService.js:103,130}), which always set
	 * {@code status: 'ativo'} for a lead the public site creates.
	 *
	 * <p>ASSUNÇÃO (already recorded in {@code backlog/tasks/TASK-010.md}, Notas): {@link
	 * InternalLeadRequest#mensagem()} is stored into {@link Lead#getNotas()}, not {@link
	 * Lead#getMensagem()} — see {@link InternalLeadRequest}'s Javadoc for why.
	 *
	 * @param request the validated payload
	 * @return the created lead
	 * @throws ResourceNotFoundException if {@code request.carroId()} is given but no car has that id
	 *     (mapped to 404)
	 */
	@Transactional
	public LeadResponse createFromWebsite(InternalLeadRequest request) {
		Lead lead = new Lead();
		lead.setNome(request.nome());
		lead.setTelefone(request.telefone());
		lead.setEmail(request.email());
		lead.setNotas(request.mensagem());
		lead.setCar(request.carroId() != null ? requireCar(request.carroId()) : null);
		lead.setCarroMarca(request.carroMarca());
		lead.setCarroModelo(request.carroModelo());
		lead.setStatus(LeadStatus.ATIVO);
		lead.setOrigem(LeadOrigin.fromValue(request.origem()));
		Lead saved = leadRepository.save(lead);
		notificationService.createForNewLead(saved);
		return LeadResponse.from(saved);
	}

	private Lead findOrThrow(UUID id) {
		return leadRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lead nao encontrado: " + id));
	}

	private Car requireCar(UUID carId) {
		return carRepository.findById(carId).orElseThrow(() -> new ResourceNotFoundException("Carro nao encontrado: " + carId));
	}

	private void applyContactFields(Lead lead, LeadRequest request) {
		lead.setNome(request.nome());
		lead.setTelefone(request.telefone());
		lead.setEmail(request.email());
		lead.setNotas(request.notas());
		lead.setStatus(LeadStatus.fromValue(request.status()));
		lead.setFollowUpDate(request.followUpDate());
	}
}

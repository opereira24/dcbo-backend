package pt.diamondcars.dcbobackend.service;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.diamondcars.dcbobackend.domain.lead.Lead;
import pt.diamondcars.dcbobackend.domain.notification.Notification;
import pt.diamondcars.dcbobackend.domain.notification.NotificationRepository;
import pt.diamondcars.dcbobackend.web.dto.NotificationResponse;
import pt.diamondcars.dcbobackend.web.exception.ResourceNotFoundException;

/**
 * Business logic for the whole {@code /api/notifications} surface plus the notification created as
 * a side effect of {@code POST /internal/leads} (TASK-010, requirements 3–4), functionally
 * equivalent to the notification-related exports of {@code dcbo/src/services/firebaseService.js}
 * the requirements list ({@code addNotification}:583, {@code markNotificationAsRead}:614, {@code
 * deleteNotification}:625).
 *
 * <p>Every public method is {@code @Transactional} and maps its result to a {@link
 * NotificationResponse} before returning, mirroring {@code CarService}'s reasoning: with {@code
 * spring.jpa.open-in-view: false}, mapping must happen while the persistence context is still
 * open.
 */
@Service
public class NotificationService {

	private final NotificationRepository notificationRepository;

	/**
	 * Creates the service with its backing repository.
	 *
	 * @param notificationRepository persistence for {@link Notification}
	 */
	public NotificationService(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	/**
	 * Lists notifications, most recently created first by default, optionally filtered by {@code
	 * read} (requirement 4).
	 *
	 * @param read required value of {@code notifications.read}, or {@code null} to not filter by it
	 * @param pageable pagination/sorting, defaulted by the controller to 50 per page sorted by
	 *     {@code createdAt} descending
	 * @return the requested page, mapped to {@link NotificationResponse}
	 */
	@Transactional(readOnly = true)
	public Page<NotificationResponse> list(Boolean read, Pageable pageable) {
		return notificationRepository
				.findAll(NotificationSpecifications.matching(read), pageable)
				.map(NotificationResponse::from);
	}

	/**
	 * Marks a notification as read. Idempotent: calling this on an already-read notification simply
	 * returns it unchanged, rather than failing or double-processing anything (requirement 4,
	 * acceptance criterion "é idempotente").
	 *
	 * @param id the notification's id
	 * @return the updated (or already-read) notification
	 * @throws ResourceNotFoundException if no notification has this id (mapped to 404)
	 */
	@Transactional
	public NotificationResponse markAsRead(UUID id) {
		Notification notification = findOrThrow(id);
		notification.setRead(true);
		return NotificationResponse.from(notification);
	}

	/**
	 * Marks every currently-unread notification as read, within a single transaction — equivalent to
	 * calling {@link #markAsRead(UUID)} once per unread notification, but without one round trip per
	 * row.
	 */
	@Transactional
	public void markAllAsRead() {
		notificationRepository.findByReadFalse().forEach(notification -> notification.setRead(true));
	}

	/**
	 * Deletes a notification.
	 *
	 * @param id the notification's id
	 * @throws ResourceNotFoundException if no notification has this id (mapped to 404)
	 */
	@Transactional
	public void delete(UUID id) {
		Notification notification = findOrThrow(id);
		notificationRepository.delete(notification);
	}

	/**
	 * Creates the notification that always accompanies a new website lead (TASK-010, requirement 3),
	 * called by {@code LeadService#createFromWebsite} within the same transaction as the lead's own
	 * creation, so both rows are committed — or rolled back — together.
	 *
	 * <p>There is no equivalent "new lead" notification in {@code dcbo} today (only the unrelated
	 * follow-up reminders {@code dcbo/src/App.js:141-194} raises client-side by polling); ASSUNÇÃO:
	 * this introduces a new {@code tipo = "novo_lead"}, styled the same way ({@code titulo}/{@code
	 * mensagem}/{@code prioridade}) as the existing {@code follow_up}/{@code follow_up_atrasado}
	 * notifications those lines build, so the back-office notification bell has exactly one
	 * consistent shape to render regardless of which flow raised the notification.
	 *
	 * @param lead the just-created, already-persisted lead this notification is about
	 * @return the created, persisted notification
	 */
	@Transactional
	public Notification createForNewLead(Lead lead) {
		String carroInfo =
				lead.getCarroMarca() != null ? " sobre " + lead.getCarroMarca() + " " + lead.getCarroModelo() : "";
		Notification notification =
				Notification.builder()
						.tipo("novo_lead")
						.titulo("Novo Lead")
						.mensagem("Novo lead de " + lead.getNome() + carroInfo)
						.prioridade("media")
						.lead(lead)
						.build();
		return notificationRepository.save(notification);
	}

	private Notification findOrThrow(UUID id) {
		return notificationRepository
				.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Notificacao nao encontrada: " + id));
	}
}

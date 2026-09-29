package pt.diamondcars.dcbobackend.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import pt.diamondcars.dcbobackend.domain.notification.Notification;

/**
 * Response payload for every {@code /api/notifications} endpoint (TASK-010, requirement 4). Never
 * the JPA entity itself (mirrors {@link CarResponse}), so the persistence model can evolve without
 * breaking the contract consumers depend on.
 *
 * <p>{@link #leadId} follows the same Camada 3 rule ADR-001 (@{@code backlog/CONVENTIONS.md})
 * already applies to {@link CarResponse#partnerId()}: the {@code lead_id} column/{@link
 * Notification#getLead()} association are English, and here the JSON key is English too — unlike
 * {@code clienteId}/{@code carroId}, no existing frontend key pins {@code leadId} to Portuguese
 * (the {@code dcbo/src/pages/notifications.js} page consumed today is still static mock data, not
 * wired to a real backend field), so this task is free to pick the idiomatic English name.
 *
 * @param id the notification's identifier
 * @param tipo the notification's type, e.g. {@code "follow_up"}, {@code "follow_up_atrasado"},
 *     {@code "novo_lead"}
 * @param titulo short title, or {@code null}
 * @param mensagem the notification's message
 * @param prioridade priority, e.g. {@code "alta"}/{@code "media"}, or {@code null}
 * @param leadId id of the {@link pt.diamondcars.dcbobackend.domain.lead.Lead} this notification is
 *     about, or {@code null}
 * @param read whether this notification has been marked as read
 * @param createdAt creation timestamp
 */
public record NotificationResponse(
		UUID id,
		String tipo,
		String titulo,
		String mensagem,
		String prioridade,
		UUID leadId,
		boolean read,
		OffsetDateTime createdAt) {

	/**
	 * Builds the response for a given, fully-loaded {@link Notification}.
	 *
	 * <p>Must only be called while the {@link Notification}'s persistence context is still open:
	 * {@link Notification#getLead()} is a lazy association, and accessing it after the session
	 * closes would raise a {@code LazyInitializationException}.
	 *
	 * @param notification the notification to map, never {@code null}
	 * @return the corresponding response DTO
	 */
	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
				notification.getId(),
				notification.getTipo(),
				notification.getTitulo(),
				notification.getMensagem(),
				notification.getPrioridade(),
				notification.getLead() != null ? notification.getLead().getId() : null,
				notification.isRead(),
				notification.getCreatedAt());
	}
}

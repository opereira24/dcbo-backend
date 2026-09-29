package pt.diamondcars.dcbobackend.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import pt.diamondcars.dcbobackend.domain.lead.Lead;

/**
 * Response payload for every {@code /api/leads} and {@code /internal/leads} endpoint, the
 * read-side counterpart of {@link LeadRequest}/{@link InternalLeadRequest}. Never the JPA entity
 * itself (mirrors {@link CarResponse}), so the persistence model can evolve without breaking the
 * contract consumers depend on.
 *
 * <p>{@link #carroId} keeps its Portuguese/frontend JSON name even though the underlying column/
 * Java association field are English ({@code car_id}/{@link Lead#getCar()}) — the Camada 3 mapping
 * {@code backlog/CONVENTIONS.md} (ADR-001) fixes: FK columns are English, JSON keys stay what
 * {@code dcbo/src/App.js:230}/{@code dcbo/src/pages/leads.js:49,87} already write.
 *
 * @param id the lead's identifier
 * @param nome full name
 * @param email email address, or {@code null}/blank if not given
 * @param telefone phone number
 * @param mensagem the free-text message a public-site visitor entered, or {@code null} — always
 *     {@code null} for every lead created through this API today, since {@link
 *     pt.diamondcars.dcbobackend.service.LeadService#createFromWebsite} stores the site's message
 *     into {@link #notas} instead (see that method's Javadoc); kept in the response for schema
 *     completeness only
 * @param notas free-text notes, or {@code null}/blank if not given
 * @param carroId id of the car this lead is about, or {@code null}
 * @param carroMarca denormalized brand snapshot of {@link #carroId}, or {@code null}
 * @param carroModelo denormalized model snapshot of {@link #carroId}, or {@code null}
 * @param carroPreco denormalized price snapshot of {@link #carroId}, or {@code null}
 * @param followUpDate date of the next scheduled follow-up, or {@code null}
 * @param status the lead's current pipeline status
 * @param origem where this lead came from ({@code "website"}, {@code "website-contacto"} or {@code
 *     "backoffice"})
 * @param createdAt creation timestamp
 * @param updatedAt last-update timestamp
 */
public record LeadResponse(
		UUID id,
		String nome,
		String email,
		String telefone,
		String mensagem,
		String notas,
		UUID carroId,
		String carroMarca,
		String carroModelo,
		BigDecimal carroPreco,
		LocalDate followUpDate,
		String status,
		String origem,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	/**
	 * Builds the response for a given, fully-loaded {@link Lead}.
	 *
	 * <p>Must only be called while the {@link Lead}'s persistence context is still open (i.e. from
	 * within the {@code @Transactional} service method that loaded it): {@link Lead#getCar()} is a
	 * lazy association, and accessing it after the session closes would raise a {@code
	 * LazyInitializationException}.
	 *
	 * @param lead the lead to map, never {@code null}
	 * @return the corresponding response DTO
	 */
	public static LeadResponse from(Lead lead) {
		return new LeadResponse(
				lead.getId(),
				lead.getNome(),
				lead.getEmail(),
				lead.getTelefone(),
				lead.getMensagem(),
				lead.getNotas(),
				lead.getCar() != null ? lead.getCar().getId() : null,
				lead.getCarroMarca(),
				lead.getCarroModelo(),
				lead.getCarroPreco(),
				lead.getFollowUpDate(),
				lead.getStatus().getValue(),
				lead.getOrigem().getValue(),
				lead.getCreatedAt(),
				lead.getUpdatedAt());
	}
}

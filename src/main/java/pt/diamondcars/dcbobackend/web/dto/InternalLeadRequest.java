package pt.diamondcars.dcbobackend.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request payload for {@code POST /internal/leads} (TASK-010, requirement 2), accepted only from
 * {@code catalog-backend} (protected by {@link
 * pt.diamondcars.dcbobackend.config.InternalTokenFilter}, never by an Auth0 JWT), mirroring the
 * shape {@code dc/src/services/firebaseService.js:95-107,122-134} already produces for both a
 * car-specific lead ({@code createLead}) and a general contact lead ({@code
 * createGeneralContact}).
 *
 * <p>ASSUNÇÃO (already recorded in {@code backlog/tasks/TASK-010.md}, Notas): {@link #mensagem}
 * maps onto {@link pt.diamondcars.dcbobackend.domain.lead.Lead#getNotas()}, not {@code
 * Lead.mensagem} — {@code leadValidationSchema} only has one free-text field ({@code notas}), and
 * no {@code dcbo} screen ever reads {@code Lead.mensagem}, so the public site's free-text message
 * is what a back-office user sees when they open the lead's notes. See {@code
 * LeadService#createFromWebsite}.
 *
 * <p>Never carries {@link #carroPreco}: {@code dc/src/services/firebaseService.js} never sends it
 * from the public site (only {@code dcbo/src/components/lead-form.js} does, for a back-office
 * lead), so {@link pt.diamondcars.dcbobackend.service.LeadService#createFromWebsite} always stores
 * it as {@code null} for a website lead.
 *
 * @param nome full name, required, 2 to 100 characters ({@code validateName})
 * @param email optional, but must be a valid address when present
 * @param telefone required, Portuguese mobile/landline format ({@code PATTERNS.PHONE_PT})
 * @param mensagem the free-text message the site visitor entered, optional
 * @param carroId id of the {@code Car} this lead is about, or {@code null} for a general contact
 *     lead ({@code createGeneralContact})
 * @param carroMarca denormalized brand snapshot of {@link #carroId}, or {@code null}
 * @param carroModelo denormalized model snapshot of {@link #carroId}, or {@code null}
 * @param origem which public-site form submitted this lead, required, {@code "website"} or {@code
 *     "website-contacto"} ({@link pt.diamondcars.dcbobackend.domain.lead.LeadOrigin})
 */
public record InternalLeadRequest(
		@NotBlank @Size(min = 2, max = 100) String nome,
		@Email @Size(max = 255) String email,
		@NotBlank @Pattern(regexp = InternalLeadRequest.PHONE_REGEXP) String telefone,
		@Size(max = 1000) String mensagem,
		UUID carroId,
		@Size(max = 100) String carroMarca,
		@Size(max = 100) String carroModelo,
		@NotBlank @Pattern(regexp = InternalLeadRequest.ORIGEM_REGEXP) String origem) {

	/** Equivalent to {@code ClientRequest#PHONE_REGEXP} — leads share the same phone format. */
	static final String PHONE_REGEXP = "^(\\+351\\s?)?[29]\\d{8}$";

	/**
	 * The only two origins a public-site submission may declare ({@link
	 * pt.diamondcars.dcbobackend.domain.lead.LeadOrigin#WEBSITE}/{@link
	 * pt.diamondcars.dcbobackend.domain.lead.LeadOrigin#WEBSITE_CONTACTO}) — never {@code
	 * "backoffice"}, which only {@code LeadService#create} may set. Rejecting an unknown/mismatched
	 * value here with 400 avoids it reaching {@code LeadOrigin#fromValue(String)} and surfacing as an
	 * unmapped {@link IllegalArgumentException}.
	 */
	static final String ORIGEM_REGEXP = "^(website|website-contacto)$";
}

package pt.diamondcars.dcbobackend.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for {@code POST /api/leads} and {@code PUT /api/leads/{id}} (TASK-010,
 * requirement 1), replicating the validation rules of {@code
 * dcbo/src/utils/validation.js:389-400} ({@code leadValidationSchema}: {@code nome}, {@code
 * telefone}, {@code email}, {@code notas}) plus the fields that schema deliberately leaves
 * untouched but {@code dcbo/src/components/lead-form.js} still submits alongside it ({@code
 * status}, {@code followUpDate}, and — only on creation, see {@code LeadService#create} — the
 * {@code carroId}/{@code carroMarca}/{@code carroModelo}/{@code carroPreco} snapshot of the
 * selected car).
 *
 * <p>Unlike {@link CarRequest}/{@link ClientRequest}, this single record is <em>not</em> applied
 * identically by both endpoints: {@link pt.diamondcars.dcbobackend.service.LeadService#update}
 * deliberately ignores {@link #carroId}/{@link #carroMarca}/{@link #carroModelo}/{@link
 * #carroPreco}, mirroring {@code updateLead} ({@code dcbo/src/services/firebaseService.js:525-545})
 * exactly — it never touches those fields either, only {@code nome}/{@code telefone}/{@code
 * email}/{@code notas}/{@code status}/{@code followUpDate}. See that method's Javadoc for why.
 *
 * <p>Never exposes or accepts an {@code id}: the entity identifier always comes from the URL path
 * (mirrors {@link CarRequest}). Never accepts {@code origem}: it is always set server-side ({@code
 * 'backoffice'} on create — TASK-010 requirement 6), never by the caller.
 *
 * @param nome full name, required, 2 to 100 characters ({@code validateName})
 * @param telefone required, Portuguese mobile/landline format ({@code PATTERNS.PHONE_PT})
 * @param email optional, but must be a valid address when present ({@code validateEmail})
 * @param notas optional, free text, max 1000 chars ({@code LIMITS.TEXT_LONG})
 * @param status the lead's pipeline status, required, one of the {@code leads.status} {@code
 *     CHECK} values ({@code V1__init.sql:115-117})
 * @param followUpDate optional date of the next scheduled follow-up
 * @param carroId id of the {@code Car} this lead is about, or {@code null} for a general contact
 *     lead ({@code dc/src/services/firebaseService.js:127}); only applied on creation
 * @param carroMarca denormalized brand snapshot of {@link #carroId} at lead time; only applied on
 *     creation
 * @param carroModelo denormalized model snapshot of {@link #carroId} at lead time; only applied on
 *     creation
 * @param carroPreco denormalized price snapshot of {@link #carroId} at lead time; only applied on
 *     creation
 */
public record LeadRequest(
		@NotBlank @Size(min = 2, max = 100) String nome,
		@NotBlank @Pattern(regexp = LeadRequest.PHONE_REGEXP) String telefone,
		@Email @Size(max = 255) String email,
		@Size(max = 1000) String notas,
		@NotBlank @Pattern(regexp = LeadRequest.STATUS_REGEXP) String status,
		LocalDate followUpDate,
		UUID carroId,
		@Size(max = 100) String carroMarca,
		@Size(max = 100) String carroModelo,
		@DecimalMin("0") BigDecimal carroPreco) {

	/** Equivalent to {@code ClientRequest#PHONE_REGEXP} — leads share the same phone format. */
	static final String PHONE_REGEXP = "^(\\+351\\s?)?[29]\\d{8}$";

	/**
	 * Every value the {@code leads.status} {@code CHECK} constraint allows ({@code
	 * V1__init.sql:115-117}), so an unknown status is rejected here with 400 instead of reaching
	 * {@link pt.diamondcars.dcbobackend.domain.lead.LeadStatus#fromValue(String)} and surfacing as an
	 * unmapped {@link IllegalArgumentException}.
	 */
	static final String STATUS_REGEXP =
			"^(ativo|contactado|test_drive_marcado|test_drive_realizado|proposta_feita|negociacao|vendido|desistiu)$";
}

package pt.diamondcars.dcbobackend.domain.lead;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import pt.diamondcars.dcbobackend.domain.car.Car;
import pt.diamondcars.dcbobackend.domain.support.AbstractAuditableDomainEntity;

/**
 * A sales lead mapped over the {@code leads} table of {@code V1__init.sql}, submitted either by
 * the public site ({@code dc}) or created directly in the back-office ({@code dcbo}).
 *
 * <p>{@link #car} maps the {@code carro_id} column (Portuguese in the schema) to a Java field
 * named {@code car} (English) via an explicit {@link JoinColumn} — this is what makes the {@link
 * LeadRepository#findByCarIdOrderByCreatedAtDesc(java.util.UUID)} derived query name from TASK-006
 * requirement 7 resolve correctly (Spring Data parses {@code CarId} as the nested property path
 * {@code car.id}). It is a Java-level naming choice, independent of the pending planner decision
 * on the FK column naming convention itself (see {@code backlog/reviews/TASK-005-r2.md}, note 1).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"nome", "telefone", "status", "origem"})
@Entity
@Table(name = "leads")
public class Lead extends AbstractAuditableDomainEntity {

	@Column(name = "nome", nullable = false, length = 255)
	private String nome;

	@Column(name = "email", length = 255)
	private String email;

	@Column(name = "telefone", nullable = false, length = 50)
	private String telefone;

	@Column(name = "mensagem", columnDefinition = "TEXT")
	private String mensagem;

	@Column(name = "notas", columnDefinition = "TEXT")
	private String notas;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "carro_id")
	private Car car;

	@Column(name = "carro_marca", length = 100)
	private String carroMarca;

	@Column(name = "carro_modelo", length = 100)
	private String carroModelo;

	@Column(name = "carro_preco", precision = 12, scale = 2)
	private BigDecimal carroPreco;

	@Column(name = "follow_up_date")
	private LocalDate followUpDate;

	@Builder.Default
	@Column(name = "status", nullable = false, length = 50)
	private LeadStatus status = LeadStatus.CONTACTADO;

	@Builder.Default
	@Column(name = "origem", nullable = false, length = 100)
	private LeadOrigin origem = LeadOrigin.WEBSITE;
}

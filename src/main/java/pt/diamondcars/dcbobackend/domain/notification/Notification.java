package pt.diamondcars.dcbobackend.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import pt.diamondcars.dcbobackend.domain.lead.Lead;
import pt.diamondcars.dcbobackend.domain.support.AbstractDomainEntity;

/**
 * A back-office notification (e.g. an overdue follow-up alert) mapped over the {@code
 * notifications} table of {@code V1__init.sql}.
 *
 * <p>{@code tipo}/{@code prioridade} are plain strings, not {@link
 * pt.diamondcars.dcbobackend.domain.support.PersistentEnum}s: TASK-006 requirement 8 only lists
 * {@code role}, transaction {@code tipo}, and lead {@code status}/{@code origem} as needing enum
 * mapping, and unlike those, the real values written for notifications (e.g. {@code
 * dcbo/src/App.js:169} {@code follow_up}/{@code follow_up_atrasado}, {@code
 * dcbo/src/pages/settings.js:69} {@code aviso}) are not exhaustively enumerable from the code
 * inspected for this task, and the column has no {@code CHECK} constraint to anchor a closed set
 * against.
 *
 * <p>{@code lead_id} cascades {@code ON DELETE CASCADE} at the database level (a notification has
 * no meaning once its lead is gone), so no JPA-level cascade is declared here — the database
 * already guarantees it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"tipo", "titulo", "prioridade", "read"})
@Entity
@Table(name = "notifications")
public class Notification extends AbstractDomainEntity {

	@Column(name = "tipo", nullable = false, length = 100)
	private String tipo;

	@Column(name = "titulo", length = 255)
	private String titulo;

	@Column(name = "mensagem", nullable = false, columnDefinition = "TEXT")
	private String mensagem;

	@Column(name = "prioridade", length = 20)
	private String prioridade;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lead_id")
	private Lead lead;

	@Builder.Default
	@Column(name = "read", nullable = false)
	private boolean read = false;
}

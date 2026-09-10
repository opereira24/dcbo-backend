package pt.diamondcars.dcbobackend.domain.transaction;

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
import pt.diamondcars.dcbobackend.domain.client.Client;
import pt.diamondcars.dcbobackend.domain.partner.Partner;
import pt.diamondcars.dcbobackend.domain.support.AbstractAuditableDomainEntity;

/**
 * A financial movement (car purchase/sale, commission, or generic income/expense) mapped over the
 * {@code transactions} table of {@code V1__init.sql}.
 *
 * <p>{@code data} is a calendar date, not an instant: it is always sourced from an {@code
 * <input type="date">} in the two React frontends (see the schema header comment in {@code
 * V1__init.sql:8-12}), so it is modeled as {@link LocalDate}, never {@link
 * java.time.OffsetDateTime}, to avoid the timezone off-by-one the TASK-005 review fixed
 * (IMP-4).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"tipo", "valor", "categoria", "data"})
@Entity
@Table(name = "transactions")
public class Transaction extends AbstractAuditableDomainEntity {

	@Column(name = "tipo", nullable = false, length = 50)
	private TransactionType tipo;

	@Column(name = "valor", nullable = false, precision = 12, scale = 2)
	private BigDecimal valor;

	@Column(name = "descricao", length = 500)
	private String descricao;

	@Column(name = "categoria", length = 100)
	private String categoria;

	@Column(name = "data", nullable = false)
	private LocalDate data;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "car_id")
	private Car car;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "client_id")
	private Client client;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "partner_id")
	private Partner partner;
}

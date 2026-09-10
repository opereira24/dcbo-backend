package pt.diamondcars.dcbobackend.domain.car;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;
import pt.diamondcars.dcbobackend.domain.client.Client;
import pt.diamondcars.dcbobackend.domain.partner.Partner;
import pt.diamondcars.dcbobackend.domain.support.AbstractAuditableDomainEntity;

/**
 * A car in the back-office inventory, mapped over the {@code cars} table of {@code V1__init.sql}
 * (plus the FK rename of {@code V2__rename_fk_columns_to_english.sql}).
 *
 * <p>Association field names follow {@code backlog/CONVENTIONS.md}, ADR-001: FK columns are
 * always English ({@link #partner} maps {@code partner_id}, {@link #client} maps
 * {@code client_id}), while scalar business attributes ({@code marca}, {@code preco}, {@code
 * vendido}, ...) stay in Portuguese because that is how the {@code dc}/{@code dcbo} frontends
 * already write them (ADR-001, Camada 2) — this class does not rename any of those.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"marca", "modelo", "ano", "preco", "vendido", "reservado", "destaque"})
@Entity
@Table(name = "cars")
public class Car extends AbstractAuditableDomainEntity {

	@Column(name = "marca", nullable = false, length = 100)
	private String marca;

	@Column(name = "modelo", nullable = false, length = 100)
	private String modelo;

	@Column(name = "ano", nullable = false)
	private int ano;

	@Column(name = "preco", nullable = false, precision = 12, scale = 2)
	private BigDecimal preco;

	@Column(name = "km", nullable = false)
	private int km;

	@Column(name = "cor", nullable = false, length = 50)
	private String cor;

	@Column(name = "combustivel", nullable = false, length = 50)
	private String combustivel;

	@Column(name = "transmissao", nullable = false, length = 50)
	private String transmissao;

	@Column(name = "origem", nullable = false, length = 100)
	private String origem;

	@Column(name = "descricao", columnDefinition = "TEXT")
	private String descricao;

	@Column(name = "preco_compra", precision = 12, scale = 2)
	private BigDecimal precoCompra;

	@Builder.Default
	@Column(name = "garantia_meses", nullable = false)
	private int garantiaMeses = 0;

	@Builder.Default
	@Column(name = "destaque", nullable = false)
	private boolean destaque = false;

	@Builder.Default
	@Column(name = "is_consignacao", nullable = false)
	private boolean consignacao = false;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "partner_id")
	private Partner partner;

	@Column(name = "commission_value", precision = 12, scale = 2)
	private BigDecimal commissionValue;

	@Column(name = "data_compra")
	private LocalDate dataCompra;

	@Builder.Default
	@Column(name = "vendido", nullable = false)
	private boolean vendido = false;

	@Builder.Default
	@Column(name = "reservado", nullable = false)
	private boolean reservado = false;

	@Column(name = "data_venda")
	private OffsetDateTime dataVenda;

	@Column(name = "preco_venda", precision = 12, scale = 2)
	private BigDecimal precoVenda;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "client_id")
	private Client client;

	/**
	 * {@code @BatchSize} fetches the {@link CarImage} collections of up to 50 cars in a single
	 * {@code IN (...)} query, instead of one query per car (IMPORTANTE 5, {@code
	 * backlog/reviews/TASK-008-r1.md}: measured at 22 statements for a 20-car page before this
	 * annotation — 1 count + 1 select + 20 per-car collection fetches). Preferred here over {@code
	 * @EntityGraph}/join-fetch on the paginated query, which would apply the page's {@code LIMIT} to
	 * the joined row count instead of the car count, silently truncating pages for cars with more
	 * than one photo.
	 */
	@Builder.Default
	@OneToMany(mappedBy = "car", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	@BatchSize(size = 50)
	private List<CarImage> images = new ArrayList<>();

	/**
	 * Adds a photo to this car, keeping both sides of the bidirectional {@code car_images}
	 * association in sync (required because {@link CarImage} is the owning side of the {@code
	 * car_id} foreign key, so Hibernate never infers it from the inverse {@link #images} side).
	 *
	 * @param image the image to attach; its {@code car} back-reference is set to {@code this}
	 */
	public void addImage(CarImage image) {
		images.add(image);
		image.setCar(this);
	}

	/**
	 * Removes a photo from this car, keeping both sides of the bidirectional association in sync
	 * and letting {@code orphanRemoval = true} delete the row on flush.
	 *
	 * @param image the image to detach
	 */
	public void removeImage(CarImage image) {
		images.remove(image);
		image.setCar(null);
	}
}

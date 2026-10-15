package pt.diamondcars.dcbobackend.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import pt.diamondcars.dcbobackend.domain.car.Car;
import pt.diamondcars.dcbobackend.domain.car.CarImage;

/**
 * Response payload for every {@code /api/cars} endpoint, the read-side counterpart of {@link
 * CarRequest}. Never the JPA entity itself (TASK-008, requirement 2), so the persistence model can
 * evolve without breaking the contract {@code dcbo}/{@code dc} already consume.
 *
 * <p>{@link #clienteId} and {@link #partnerId} keep their Portuguese/frontend JSON names even
 * though the underlying columns/Java association fields are English ({@code client_id}/{@link
 * Car#getClient()}, {@code partner_id}/{@link Car#getPartner()}) — this is exactly the Camada 3
 * mapping {@code backlog/CONVENTIONS.md} (ADR-001) fixes: FK columns are English, JSON keys stay
 * what {@code dcbo/src/App.js:304,343} already write.
 *
 * @param id the car's identifier
 * @param marca brand
 * @param modelo model
 * @param ano model year
 * @param preco sale price
 * @param km odometer reading
 * @param cor colour
 * @param combustivel fuel type
 * @param transmissao transmission type
 * @param origem provenance/origin
 * @param descricao free-text description, possibly {@code null}
 * @param precoCompra purchase price, possibly {@code null}
 * @param dataCompra purchase date, possibly {@code null}
 * @param isConsignacao whether this car is sold on consignment
 * @param partnerId id of the consignment partner, or {@code null}
 * @param commissionValue commission owed to the partner, or {@code null}
 * @param garantiaMeses warranty length in months
 * @param destaque whether this car is currently featured
 * @param vendido whether this car has been sold
 * @param reservado whether this car is currently reserved
 * @param dataVenda timestamp of the sale, or {@code null} if not sold
 * @param precoVenda the price the car was actually sold for, or {@code null} if not sold
 * @param clienteId id of the client that purchased this car, or {@code null}
 * @param images ordered list of photo URLs
 * @param imageThumbnails ordered list of thumbnail URLs, parallel to {@link #images} by index
 * @param createdAt creation timestamp
 * @param updatedAt last-update timestamp
 */
public record CarResponse(
		UUID id,
		String marca,
		String modelo,
		int ano,
		BigDecimal preco,
		int km,
		String cor,
		String combustivel,
		String transmissao,
		String origem,
		String descricao,
		BigDecimal precoCompra,
		LocalDate dataCompra,
		boolean isConsignacao,
		UUID partnerId,
		BigDecimal commissionValue,
		int garantiaMeses,
		boolean destaque,
		boolean vendido,
		boolean reservado,
		OffsetDateTime dataVenda,
		BigDecimal precoVenda,
		UUID clienteId,
		List<String> images,
		List<String> imageThumbnails,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	/**
	 * Builds the response for a given, fully-loaded {@link Car}.
	 *
	 * <p>Must only be called while the {@link Car}'s persistence context is still open (i.e. from
	 * within the {@code @Transactional} service method that loaded it): {@link Car#getImages()} and
	 * the {@link Car#getClient()}/{@link Car#getPartner()} associations are lazily fetched, and
	 * accessing them after the session closes would raise a {@code LazyInitializationException}.
	 *
	 * @param car the car to map, never {@code null}
	 * @return the corresponding response DTO
	 */
	public static CarResponse from(Car car) {
		List<String> images = car.getImages().stream().map(CarImage::getUrl).toList();
		List<String> imageThumbnails = car.getImages().stream().map(CarImage::getThumbnailUrl).toList();
		return new CarResponse(
				car.getId(),
				car.getMarca(),
				car.getModelo(),
				car.getAno(),
				car.getPreco(),
				car.getKm(),
				car.getCor(),
				car.getCombustivel(),
				car.getTransmissao(),
				car.getOrigem(),
				car.getDescricao(),
				car.getPrecoCompra(),
				car.getDataCompra(),
				car.isConsignacao(),
				car.getPartner() != null ? car.getPartner().getId() : null,
				car.getCommissionValue(),
				car.getGarantiaMeses(),
				car.isDestaque(),
				car.isVendido(),
				car.isReservado(),
				car.getDataVenda(),
				car.getPrecoVenda(),
				car.getClient() != null ? car.getClient().getId() : null,
				images,
				imageThumbnails,
				car.getCreatedAt(),
				car.getUpdatedAt());
	}
}

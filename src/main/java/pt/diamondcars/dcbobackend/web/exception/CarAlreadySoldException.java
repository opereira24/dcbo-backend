package pt.diamondcars.dcbobackend.web.exception;

import java.util.UUID;

/**
 * Thrown when {@code POST /api/cars/{id}/sell} targets a car that is already marked as sold
 * (TASK-008, bloqueador 1 of {@code backlog/reviews/TASK-008-r1.md}) — mapped by {@link
 * pt.diamondcars.dcbobackend.web.ApiExceptionHandler} to a 409 response.
 *
 * <p>A sale is a one-way transition enforced by the service layer, not just the {@code dcbo}
 * frontend: selling an already-sold car a second time would otherwise double-count the buying
 * client's {@code purchases_count}, or — when the second call omits {@code clienteId} — silently
 * detach the car from its original buyer, inflating that client's count forever with no way for
 * {@code revert-sale} to correct it afterwards.
 */
public class CarAlreadySoldException extends RuntimeException {

	/**
	 * Creates the exception with a message naming the already-sold car.
	 *
	 * @param carId id of the car that is already marked as sold
	 */
	public CarAlreadySoldException(UUID carId) {
		super("Carro ja vendido: " + carId);
	}
}

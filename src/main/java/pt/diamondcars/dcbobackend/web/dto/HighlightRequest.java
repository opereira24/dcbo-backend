package pt.diamondcars.dcbobackend.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for {@code PATCH /api/cars/{id}/highlight}, equivalent to the {@code
 * onUpdateCar} call in {@code dcbo/src/pages/highlights.js:15-30}.
 *
 * @param destaque {@code true} to feature the car, {@code false} to remove it from the featured
 *     set; setting it to {@code true} on a car that is not already featured is rejected with 409
 *     once 8 cars are already featured (TASK-008, requirement 1)
 */
public record HighlightRequest(@NotNull Boolean destaque) {}

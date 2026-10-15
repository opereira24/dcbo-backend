package pt.diamondcars.dcbobackend.web;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.diamondcars.dcbobackend.service.NotificationService;
import pt.diamondcars.dcbobackend.web.dto.NotificationResponse;

/**
 * REST API for the back-office's notification bell (TASK-010, requirement 4), functionally
 * equivalent to the notification-related exports of {@code dcbo/src/services/firebaseService.js}
 * the task requirements list. Every endpoint requires a valid Auth0 JWT (the global rule from
 * {@code pt.diamondcars.dcbobackend.config.SecurityConfig}, TASK-007).
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	/**
	 * Creates the controller with its backing service.
	 *
	 * @param notificationService the service implementing every operation below
	 */
	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	/**
	 * Lists notifications, most recently created first by default, optionally filtered by {@code
	 * read}.
	 *
	 * @param read when given, only returns notifications with this exact {@code read} value
	 * @param pageable pagination/sorting; defaults to 50 per page, sorted by {@code createdAt}
	 *     descending, when the caller does not ask for something else
	 * @return the requested page of notifications, wrapped in a {@link PagedModel}
	 */
	@GetMapping
	public PagedModel<NotificationResponse> list(
			@RequestParam(required = false) Boolean read,
			@PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(notificationService.list(read, pageable));
	}

	/**
	 * Marks a notification as read. Idempotent: calling this twice on the same notification is not
	 * an error.
	 *
	 * @param id the notification's id
	 * @return the updated notification (200), or a 404 {@code ApiError} if it does not exist
	 */
	@PatchMapping("/{id}/read")
	public NotificationResponse markAsRead(@PathVariable UUID id) {
		return notificationService.markAsRead(id);
	}

	/**
	 * Marks every currently-unread notification as read.
	 */
	@PostMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markAllAsRead() {
		notificationService.markAllAsRead();
	}

	/**
	 * Deletes a notification.
	 *
	 * @param id the notification's id
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		notificationService.delete(id);
	}
}

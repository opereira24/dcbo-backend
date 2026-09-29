package pt.diamondcars.dcbobackend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pt.diamondcars.dcbobackend.domain.user.AppUserRepository;
import pt.diamondcars.dcbobackend.web.exception.InactiveUserException;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rejects, with 403, every request from an authenticated back-office user whose local {@code
 * app_users} profile has {@code active = false} (TASK-012, requirement 4) — the server-side
 * counterpart of {@code deactivateUser}/{@code activateUser} in {@code
 * dcbo/src/services/firebaseService.js:714-737}, which previously only stopped a deactivated
 * account from being used in the Firestore-backed frontend, not from calling the API directly with
 * an otherwise still-valid session token.
 *
 * <p>Implemented as a single class that is both the {@link HandlerInterceptor} itself and the
 * {@link WebMvcConfigurer} that registers it, rather than splitting the two across separate files,
 * since neither role has any other reason to exist independently in this codebase.
 *
 * <p>Runs in {@link #preHandle}, which the {@code DispatcherServlet} invokes before the controller
 * method (and, transitively, before the {@code @PreAuthorize} advice around it) — so a deactivated
 * admin is rejected here before any role check ever runs, not just on endpoints their role would
 * otherwise permit. Registered against every path except {@code /actuator/**} (public health/info,
 * TASK-007) and {@code /internal/**} (service-to-service calls authenticated by {@link
 * InternalTokenFilter}, never by a back-office user's JWT) — TASK-012 requirement 4 excludes both
 * explicitly.
 */
@Configuration
public class ActiveUserInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private final AuthenticatedUserProvider authenticatedUserProvider;
	private final AppUserRepository appUserRepository;

	/**
	 * Creates the interceptor with its collaborators.
	 *
	 * @param authenticatedUserProvider resolves the Auth0 {@code sub} of the currently authenticated
	 *     caller, if any
	 * @param appUserRepository looked up by {@code sub} to find the caller's local profile, if one
	 *     exists
	 */
	public ActiveUserInterceptor(
			AuthenticatedUserProvider authenticatedUserProvider, AppUserRepository appUserRepository) {
		this.authenticatedUserProvider = authenticatedUserProvider;
		this.appUserRepository = appUserRepository;
	}

	/**
	 * Registers {@code this} as an interceptor for every path except the public actuator endpoints
	 * and the internal service-to-service endpoints (see the class Javadoc).
	 *
	 * @param registry Spring MVC's interceptor registry
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry
				.addInterceptor(this)
				.addPathPatterns("/**")
				.excludePathPatterns("/actuator/**", "/internal/**");
	}

	/**
	 * Rejects the request with {@link InactiveUserException} when the authenticated caller has a
	 * local profile and it is inactive; otherwise lets the request continue unchanged.
	 *
	 * <p>A missing authenticated subject, or an authenticated subject with no local profile yet
	 * (e.g. the very first call after an Auth0 login, before {@code POST /api/users} registers it),
	 * is not this interceptor's concern — {@code SecurityConfig} already requires authentication for
	 * everything not excluded above, and {@code AppUserController#me()} is what reports "no local
	 * profile" as 404.
	 *
	 * @param request the incoming request
	 * @param response the response (unused; rejection is signalled by throwing, not by writing
	 *     directly, so {@link pt.diamondcars.dcbobackend.web.ApiExceptionHandler} produces the same
	 *     {@code ApiError} envelope as every other mapped exception)
	 * @param handler the resolved handler for this request (unused)
	 * @return {@code true} always when it returns normally, since a rejection is signalled by
	 *     throwing instead
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		authenticatedUserProvider
				.getCurrentUserSubject()
				.flatMap(appUserRepository::findByAuthSubject)
				.filter(appUser -> !appUser.isActive())
				.ifPresent(
						appUser -> {
							throw new InactiveUserException("Conta desativada: " + appUser.getAuthSubject());
						});
		return true;
	}
}

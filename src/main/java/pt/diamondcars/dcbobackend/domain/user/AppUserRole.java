package pt.diamondcars.dcbobackend.domain.user;

import pt.diamondcars.dcbobackend.domain.support.PersistentEnum;

/**
 * Role of an {@link AppUser} inside the back-office, matching the two values of {@code
 * USER_ROLES} in {@code dcbo/src/services/userManagementService.js:124} ({@code admin}, {@code
 * user}).
 */
public enum AppUserRole implements PersistentEnum {

	ADMIN("admin"),
	USER("user");

	private final String value;

	AppUserRole(String value) {
		this.value = value;
	}

	@Override
	public String getValue() {
		return value;
	}
}

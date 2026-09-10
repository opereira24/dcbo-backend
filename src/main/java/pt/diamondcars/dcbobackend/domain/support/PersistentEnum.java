package pt.diamondcars.dcbobackend.domain.support;

/**
 * Contract implemented by every domain enum that is persisted as its own explicit string value
 * (via {@link AbstractPersistentEnumConverter}) rather than through JPA's {@code @Enumerated}.
 *
 * <p>TASK-006 requirement 8 asks for {@code role}/transaction {@code tipo}/lead {@code
 * status}/{@code origem} to be mapped as enums, never as {@code EnumType.ORDINAL}. Plain {@code
 * @Enumerated(EnumType.STRING)} was deliberately not used for these: it persists {@link
 * Enum#name()} verbatim (upper snake_case, e.g. {@code "CONTACTADO"}), which would violate the
 * lower-case string values the {@code V1__init.sql} schema already relies on — most visibly the
 * {@code leads.status} {@code CHECK} constraint, whose allowed values are all lower snake_case,
 * and {@code leads.origem = 'website-contacto'}, which contains a hyphen that cannot even be a
 * valid Java enum constant name. This converter-based approach keeps full compile-time type safety
 * in Java while never touching {@code EnumType.ORDINAL} and never fighting the existing schema.
 */
public interface PersistentEnum {

	/**
	 * Returns the exact string stored in the database column for this enum constant.
	 *
	 * @return the database representation of this constant
	 */
	String getValue();
}

package pt.diamondcars.dcbobackend.service;

import org.springframework.data.jpa.domain.Specification;
import pt.diamondcars.dcbobackend.domain.client.Client;

/**
 * Builds the {@link Specification} {@link ClientService#list} uses for the optional free-text
 * search of {@code GET /api/clients} (TASK-009, requirement 1: "filtro de pesquisa opcional por
 * nome/email/telefone/NIF"), mirroring the filter-combination approach {@link CarSpecifications}
 * already established for {@code GET /api/cars}.
 */
final class ClientSpecifications {

	private ClientSpecifications() {}

	/**
	 * Builds a specification matching clients whose {@code name}, {@code email}, {@code phone} or
	 * {@code nif} contains the given text, case-insensitively.
	 *
	 * @param search the free text to search for, or {@code null}/blank to not filter at all
	 * @return the combined specification; matches every client when {@code search} is {@code
	 *     null}/blank
	 */
	static Specification<Client> matching(String search) {
		if (search == null || search.isBlank()) {
			return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
		}
		String pattern = "%" + search.trim().toLowerCase() + "%";
		return (root, query, criteriaBuilder) ->
				criteriaBuilder.or(
						criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("name")), pattern),
						criteriaBuilder.like(
								criteriaBuilder.lower(criteriaBuilder.coalesce(root.<String>get("email"), "")),
								pattern),
						criteriaBuilder.like(
								criteriaBuilder.lower(criteriaBuilder.coalesce(root.<String>get("phone"), "")),
								pattern),
						criteriaBuilder.like(
								criteriaBuilder.lower(criteriaBuilder.coalesce(root.<String>get("nif"), "")),
								pattern));
	}
}

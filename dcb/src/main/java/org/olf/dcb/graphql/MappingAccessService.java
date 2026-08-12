package org.olf.dcb.graphql;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Who may see and change reference value mappings. Particularly important for shared system scenarios -
 * if mapping edits are enabled, staff must only be able to change their library's mappings.
 * <p>

 */
@Singleton
public class MappingAccessService {
	private static final Logger log = LoggerFactory.getLogger(MappingAccessService.class);

	/** The category whose mappings name an agency, and so can be attributed to a library. */
	private static final String AGENCY_CATEGORY = "AGENCY";

	private final AgencyScopeResolver agencyScopeResolver;
	private final ConsortiumService consortiumService;

	public MappingAccessService(AgencyScopeResolver agencyScopeResolver,
		ConsortiumService consortiumService) {

		this.agencyScopeResolver = agencyScopeResolver;
		this.consortiumService = consortiumService;
	}

	/**
	 * Narrow a mappings query to the systems this caller administers.
	 * <p>
	 * Scoped by Host LMS rather than by library, deliberately. Most mappings - patron
	 * types, item types - describe the system rather than any one of its tenants, and a
	 * library needs to read the ones that govern its own circulation even on a shared
	 * server. Reading a co-tenant's location mapping tells you which agency a branch
	 * code belongs to, which is not a secret. Changing it is, and that is scoped
	 * further in {@link #assertMayEdit}.
	 */
	public Mono<Optional<QuerySpecification<ReferenceValueMapping>>> restrict(
		DataFetchingEnvironment env, QuerySpecification<ReferenceValueMapping> specification) {

		// Optional rather than an empty Mono: "no filter at all" and "the scope has not
		// been worked out yet" are different answers and must not share a signal
		if (AgencyAccessScope.isUnrestricted(env)) {
			return Mono.just(Optional.ofNullable(specification));
		}

		return agencyScopeResolver.permittedHostLmsCodes(env)
			.defaultIfEmpty(Set.of())
			.map(permitted -> {
				if (permitted.isEmpty()) {
					log.warn("Reference value mapping query from a caller with neither a "
						+ "consortium role nor an agency claim; returning nothing");
				}

				final QuerySpecification<ReferenceValueMapping> ownSystems
					= (root, query, criteriaBuilder) -> permitted.isEmpty()
						? criteriaBuilder.disjunction()
						: criteriaBuilder.or(
							root.get("fromContext").in(permitted),
							root.get("toContext").in(permitted));

				return Optional.of(specification == null
					? ownSystems
					: specification.and(ownSystems));
			});
	}

	/**
	 * Refuse an edit this caller is not entitled to make.
	 * <p>
	 * Consortium roles are unrestricted, as they are today. A library administrator has
	 * to clear three gates - fail-safe operation
	 * <ol>
	 *   <li>the consortium has not disabled library mapping editing;</li>
	 *   <li>the mapping belongs to a Host LMS one of their agencies sits on;</li>
	 *   <li>where the mapping names an agency, it names one of theirs.</li>
	 * </ol>
	 * The third gate only applies to mappings in the AGENCY category, because that is
	 * where a mapping can be attributed to a library at all. A patron type mapping on a
	 * shared system belongs to the system rather than to any of its tenants, so it
	 * stops at the second gate: any library on that system may edit it, and a
	 * consortium that does not want that has the functional setting that they can utilise.
	 *
	 * @return empty when permitted; an error carrying the reason when not
	 */
	public Mono<Void> assertMayEdit(DataFetchingEnvironment env, ReferenceValueMapping mapping) {
		if (AgencyAccessScope.isUnrestricted(env)) {
			return Mono.empty();
		}

		return consortiumService.isEnabled(FunctionalSettingType.DENY_LIBRARY_MAPPING_EDIT)
			.defaultIfEmpty(Boolean.FALSE)
			.flatMap(editingDenied -> Boolean.TRUE.equals(editingDenied)
				? denied("Library mapping editing has been disabled by your consortium administrator.")
				: assertOwns(env, mapping));
	}

	private Mono<Void> assertOwns(DataFetchingEnvironment env, ReferenceValueMapping mapping) {
		return agencyScopeResolver.permittedHostLmsCodes(env)
			.defaultIfEmpty(Set.of())
			.flatMap(permittedSystems -> {
				if (!onOneOf(mapping, permittedSystems)) {
					log.warn("Refusing mapping edit: {}/{} is not on a Host LMS this caller administers ({})",
						getValueOrNull(mapping, ReferenceValueMapping::getFromContext),
						getValueOrNull(mapping, ReferenceValueMapping::getFromValue), permittedSystems);

					return denied("This mapping belongs to a system your library does not administer.");
				}

				if (!namesAPermittedAgency(env, mapping)) {
					log.warn("Refusing mapping edit: {} maps to agency {}, which this caller does not administer",
						getValueOrNull(mapping, ReferenceValueMapping::getFromValue),
						getValueOrNull(mapping, ReferenceValueMapping::getToValue));

					return denied("This mapping belongs to another library on the same system.");
				}

				return Mono.empty();
			});
	}

	private static boolean onOneOf(ReferenceValueMapping mapping, Set<String> permittedSystems) {
		return permittedSystems.contains(getValueOrNull(mapping, ReferenceValueMapping::getFromContext))
			|| permittedSystems.contains(getValueOrNull(mapping, ReferenceValueMapping::getToContext));
	}

	/**
	 * True when the mapping does not name an agency at all - there is then nothing to
	 * attribute it to, and the Host LMS gate is as far as this can go.
	 */
	private static boolean namesAPermittedAgency(DataFetchingEnvironment env,
		ReferenceValueMapping mapping) {

		if (!AGENCY_CATEGORY.equalsIgnoreCase(getValueOrNull(mapping, ReferenceValueMapping::getToCategory))) {
			return true;
		}

		final Collection<String> mine = AgencyAccessScope.visibleAgencyCodes(env);

		return mine.contains(getValueOrNull(mapping, ReferenceValueMapping::getToValue));
	}

	private static Mono<Void> denied(String detail) {
		return Mono.error(new HttpStatusException(HttpStatus.FORBIDDEN, detail));
	}
}

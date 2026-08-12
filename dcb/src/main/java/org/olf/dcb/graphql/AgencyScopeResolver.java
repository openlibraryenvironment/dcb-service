package org.olf.dcb.graphql;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.postgres.PostgresAgencyRepository;

import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The parts of a caller's access scope that have to be looked up.
 * <p>
 * {@link AgencyAccessScope} decides policy from the token alone and stays free of
 * I/O. This resolves the things policy needs but the token does not carry: an agency
 * claim says which libraries somebody administers, and answering "may they see this
 * Host LMS" means knowing which systems those libraries sit on.
 * <p>
 * The answer is memoised on the GraphQL context. It costs two queries per claimed
 * agency and is asked once per object in the response - a library list of a hundred
 * would otherwise repeat it a hundred times. The Mono is cached rather than its
 * value, so concurrent field resolution shares one subscription instead of racing.
 */
@Singleton
public class AgencyScopeResolver {
	private static final String PERMITTED_HOST_LMS = "permittedHostLms";

	private final PostgresAgencyRepository agencyRepository;
	private final HostLmsRepository hostLmsRepository;

	public AgencyScopeResolver(PostgresAgencyRepository agencyRepository,
		HostLmsRepository hostLmsRepository) {

		this.agencyRepository = agencyRepository;
		this.hostLmsRepository = hostLmsRepository;
	}

	/** Host LMS records behind the agencies this caller administers. */
	public Mono<Set<UUID>> permittedHostLmsIds(DataFetchingEnvironment env) {
		return permittedHostLms(env)
			.map(records -> records.stream()
				.map(DataHostLms::getId)
				.collect(Collectors.toUnmodifiableSet()));
	}

	/**
	 * The same systems by code, which is how reference value mappings name them -
	 * a mapping's context is a Host LMS code rather than a foreign key.
	 */
	public Mono<Set<String>> permittedHostLmsCodes(DataFetchingEnvironment env) {
		return permittedHostLms(env)
			.map(records -> records.stream()
				.map(DataHostLms::getCode)
				.filter(java.util.Objects::nonNull)
				.collect(Collectors.toUnmodifiableSet()));
	}

	/**
	 * Loaded through the Host LMS repository rather than read off the agency.
	 * <p>
	 * Micronaut Data materialises a many-to-one relation with only its identifier
	 * populated, so {@code agency.getHostLms().getCode()} is null on a freshly queried
	 * agency. Taking the id and fetching the record is the same thing
	 * getHostLmsForAgencyDataFetcher does, and for the same reason - a scope built from
	 * nulls quietly permits nothing, which looks exactly like a working deny rule.
	 */
	private Mono<Set<DataHostLms>> permittedHostLms(DataFetchingEnvironment env) {
		return cached(env, () -> Flux.fromIterable(AgencyAccessScope.visibleAgencyCodes(env))
			.concatMap(agencyCode -> Mono.from(agencyRepository.findOneByCode(agencyCode)))
			.mapNotNull(agency -> getValueOrNull(agency, DataAgency::getHostLms, DataHostLms::getId))
			.concatMap(hostLmsId -> Mono.from(hostLmsRepository.findById(hostLmsId)))
			.collect(Collectors.toUnmodifiableSet())
			.cache());
	}

	@SuppressWarnings("unchecked")
	private static Mono<Set<DataHostLms>> cached(DataFetchingEnvironment env,
		java.util.function.Supplier<Mono<Set<DataHostLms>>> resolve) {

		return (Mono<Set<DataHostLms>>) env.getGraphQlContext()
			.computeIfAbsent(PERMITTED_HOST_LMS, ignored -> resolve.get());
	}
}

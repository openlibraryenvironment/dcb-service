package org.olf.dcb.core.api;

import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Anonymous, non-sensitive library directory for discovery services: code,
 * host LMS code and lat/long only
 *
 * Discovery will use it to resolve "the library nearest to me" into a concrete
 * hostLms code it can scope a search by.
 */
@Controller("/discovery/libraries")
@Secured(IS_ANONYMOUS)
@Tag(name = "Discovery API")
@Slf4j
public class DiscoveryLibrariesController {

	private final AgencyRepository agencyRepository;
	private final HostLmsRepository hostLmsRepository;

	public DiscoveryLibrariesController(AgencyRepository agencyRepository,
			HostLmsRepository hostLmsRepository) {
		this.agencyRepository = agencyRepository;
		this.hostLmsRepository = hostLmsRepository;
	}

	@Operation(summary = "List libraries",
		description = "Anonymous directory of libraries (code, host LMS code, name, lat/long) for discovery: "
			+ "the institution picker at login and 'nearest library' resolution. Coordinates may be null; "
			+ "consumers that need them (nearest library) filter accordingly. "
			+ "By default only libraries enabled for borrowing are returned, because a patron from a "
			+ "non-borrowing agency cannot place a request. Pass includeAll=true for the whole directory.")
	@Get
	public Mono<List<LibraryGeo>> list(
		@QueryValue(defaultValue = "false") boolean includeAll) {

		final var agencies = includeAll
			? agencyRepository.queryAll()
			: agencyRepository.findAllBorrowingAgencies();
		return Flux.from(agencies)
			.flatMap(this::toLibraryGeo)
			.collectList();
	}

	// queryAll() does not fetch the hostLms association, so resolve its code
	// explicitly — same workaround the /agencies endpoint uses.
	private Mono<LibraryGeo> toLibraryGeo(DataAgency agency) {
		return Mono.from(agencyRepository.findHostLmsIdById(agency.getId()))
			.flatMap(hostLmsId -> Mono.from(hostLmsRepository.findById(hostLmsId)))
			.map(hostLms -> new LibraryGeo(agency.getCode(), hostLms.getCode(),
				agency.getName(), agency.getLatitude(), agency.getLongitude()))
			.doOnError(error -> log.warn("Could not resolve host LMS for agency {}", agency.getCode(), error))
			.onErrorResume(error -> Mono.empty());
	}

	@Serdeable
	public record LibraryGeo(
		String code,
		String hostLmsCode,
		String name,
		Double latitude,
		Double longitude) {
	}
}

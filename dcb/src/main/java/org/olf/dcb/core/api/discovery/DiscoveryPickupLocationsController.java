package org.olf.dcb.core.api.discovery;

import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.security.OpenToAllPrincipals;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.FunctionalSettingRepository;
import org.olf.dcb.storage.LocationRepository;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Where a patron of a library may collect a request.
 */
@Controller("/discovery/pickup-locations")
@Secured(IS_ANONYMOUS)
@OpenToAllPrincipals(justification =
	"The pickup choices a discovery layer shows before sign-in, as public as the library directory. Read-only, no patron data, no authority conferred, at most 100 rows a page.")
@Tag(name = "Discovery API")
public class DiscoveryPickupLocationsController {

	static final int MAX_PAGE_SIZE = 100;

	private final AgencyRepository agencyRepository;
	private final FunctionalSettingRepository functionalSettingRepository;
	private final LocationRepository locationRepository;

	public DiscoveryPickupLocationsController(AgencyRepository agencyRepository,
		FunctionalSettingRepository functionalSettingRepository,
		LocationRepository locationRepository) {

		this.agencyRepository = agencyRepository;
		this.functionalSettingRepository = functionalSettingRepository;
		this.locationRepository = locationRepository;
	}

	@Operation(summary = "Pickup locations for a library's patrons",
		description = "The locations a patron of agencyCode may choose as pickupLocationCode when "
			+ "placing a request: send a location's id, not its code. When the library has "
			+ "pickup-anywhere enabled this includes other libraries' locations enabled for it. "
			+ "Ordered by library and then location name, at most " + MAX_PAGE_SIZE + " per page; "
			+ "hasMore says whether to ask for the next page. 404 when no library has that code.")
	@Get
	public Mono<HttpResponse<PickupLocations>> list(@QueryValue String agencyCode,
		@QueryValue(defaultValue = "0") int page,
		@QueryValue(defaultValue = "" + MAX_PAGE_SIZE) int size) {

		final var pageNumber = Math.max(page, 0);
		final var pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);

		return Mono.from(agencyRepository.findOneByCode(agencyCode))
			.flatMap(agency -> isPickupAnywhereEnabled(agency.getId())
				.flatMap(pickupAnywhere -> findPage(agency.getId(), pickupAnywhere,
					pageNumber, pageSize)))
			.<HttpResponse<PickupLocations>>map(HttpResponse::ok)
			.defaultIfEmpty(HttpResponse.notFound());
	}

	private Mono<Boolean> isPickupAnywhereEnabled(UUID agencyId) {
		return Mono.from(functionalSettingRepository.isSettingEnabledForAgency(agencyId,
				FunctionalSettingType.PICKUP_ANYWHERE.toString()))
			.defaultIfEmpty(false);
	}

	// One row past the page is fetched to learn whether another page exists without a count
	private Mono<PickupLocations> findPage(UUID agencyId, boolean pickupAnywhere, int page,
		int size) {

		return Flux.from(locationRepository.findPickupLocationsForPatronAgency(agencyId,
				pickupAnywhere, size + 1, (long) page * size))
			.map(entry -> new PickupLocation(entry.id(), entry.code(), entry.name(),
				entry.agencyCode(), entry.agencyName()))
			.collectList()
			.map(rows -> new PickupLocations(rows.stream().limit(size).toList(), page, size,
				rows.size() > size));
	}

	@Serdeable
	public record PickupLocations(List<PickupLocation> locations, int page, int size,
		boolean hasMore) {
	}

	/** {@code id} is what /discovery/requests/place takes as pickupLocationCode. */
	@Serdeable
	public record PickupLocation(UUID id, String code, String name, String agencyCode,
		String agencyName) {
	}
}

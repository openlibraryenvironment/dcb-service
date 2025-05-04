package org.olf.dcb.core.svc;

import java.util.UUID;

import org.olf.dcb.core.model.Location;
import org.olf.dcb.storage.LocationRepository;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import io.micronaut.transaction.annotation.Transactional;

@Slf4j
@Singleton
public class LocationService {

	private final LocationRepository locationRepository;

	public LocationService(LocationRepository locationRepository) {
		this.locationRepository = locationRepository;
	}

	public Mono<Location> findById(@NotNull @NonNull UUID id) {
		return Mono.from(locationRepository.findById(id));
	}

	public Mono<Location> findByCode(@NotNull @NonNull String pickupLocationCode) {
		return Mono.from(locationRepository.findOneByCode(pickupLocationCode));
	}
	
	public Mono<Location> findByIdOrCode( @Nullable String idOrCode ) {
		if ( idOrCode == null) return Mono.empty();
		
		return Mono.just( idOrCode )
			.flatMap( this::findById )
			.switchIfEmpty( Mono.defer(() -> findByCode( idOrCode )));
	}

	/**
	 * Attempt to convert string ID into a UUID before finding by ID
	 *
	 * @param id the ID to find a location for as a string
	 * @return empty if the ID is not a UUID, otherwise the result of finding a location by ID
	 */
	public Mono<Location> findById(@Nullable String id) {
		
		if (StringUtils.isEmpty(id)) return Mono.empty();
		
		try {
			final var parsedId = UUID.fromString(id);

			return findById(parsedId);
		}
		// Is not a UUID
		catch (IllegalArgumentException exception) {
			log.warn("Location ID: \"{}\" is not a valid UUID", id, exception);

			return Mono.empty();
		}
	}

	/**
	 * Functions such as cache holdings can emit location data (where the item is held). Sometimes these item records need to be enriched
	 * by a user. For example, in shared systems it is useful to know which agency a location should be associated with. In order to do that
	 * it is useful to remember Locations we have seen so that they may be amended later on. Memoize location takes a statically generated
	 * location from these functions, and if no record of that exists in the repository uses it to bootstrap a new DB record, otherwise
	 * it returns the record as passed in.
 	 */
	@Transactional
	public Mono<Location> memoize(Location location) {
		return Mono.from(locationRepository.findById(location.getId()))
			.switchIfEmpty(Mono.from(locationRepository.save(location)));
	}
}

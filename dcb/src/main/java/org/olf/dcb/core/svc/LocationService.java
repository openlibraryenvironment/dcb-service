package org.olf.dcb.core.svc;

import java.time.Duration;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.Workflow;
import org.olf.dcb.storage.LocationRepository;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import io.micronaut.transaction.annotation.Transactional;
import services.k_int.utils.UUIDUtils;

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
	 * Record a location an item was reported at, so that a human can map it later.
	 * <p>
	 * Availability emits the branch each item is held at. On a shared system that
	 * branch is the only thing identifying which library owns the item, and it is
	 * useless to DCB until someone has created a location-to-agency mapping for it.
	 * Remembering the branch - flagged for review when it has no agency - is how an
	 * operator finds out which mappings a sixty-branch system still needs, without
	 * enumerating the ILS by hand.
	 * <p>
	 * <strong>The agency is deliberately allowed to be null, and that is the whole
	 * point.</strong> This method previously required one, and no adapter has ever set
	 * an agency on an item's location - so it rejected every location and created
	 * nothing, for the entire time it existed. An unmapped branch is precisely the
	 * branch worth recording; a mapped one DCB already understands.
	 * <p>
	 * Existing rows are found by (host system, code) rather than by a generated id.
	 * Ids for locations created through the admin UI and config import are derived
	 * from the agency code, which an unmapped location does not have - looking up by
	 * a host-derived id would miss those rows and insert a duplicate alongside every
	 * one of them. Looking rows up rather than re-keying them is also what keeps this
	 * off the schema: nothing has to be migrated.
	 *
	 * @param location as reported by the adapter; only its code and name are relied on
	 * @param agency the agency the location resolved to, or null if it did not resolve
	 * @param hostSystem the system the item came from. Required - a location code
	 * means nothing without it
	 * @return the existing or newly created location, or empty when there is nothing
	 * to do - either too little to identify the location, or it was recorded recently
	 * enough to still be in {@link #recentlySeen}
	 */
	@Transactional
	public Mono<Location> memoize(Location location, DataAgency agency, DataHostLms hostSystem) {
		log.debug("Memoize {} (agency: {}, hostSystem: {})", location, agency, hostSystem);

		if (location == null || location.getCode() == null || hostSystem == null) {
			return Mono.empty();
		}

		final var seenKey = seenKey(hostSystem, location.getCode());

		if (recentlySeen.getIfPresent(seenKey) != null) {
			return Mono.empty();
		}

		return Mono.from(locationRepository.findOneByHostSystemAndCode(hostSystem, location.getCode()))
			.switchIfEmpty(Mono.defer(() -> dynamicCreateLocation(
				discovered(location, agency, hostSystem))))
			.doOnNext(recorded -> recentlySeen.put(seenKey, Boolean.TRUE));
	}

	/**
	 * Locations already recorded, so the availability path does not re-read them.
	 * <p>
	 * Without this, remembering locations costs a select per item per availability
	 * check for ever - the first check does the useful work and every subsequent one
	 * repeats a query whose answer cannot have changed. A title held at sixty branches
	 * of one Koha would pay sixty reads to learn nothing.
	 * <p>
	 * Bounded on both size and age. The keys are (system, location code) pairs, which
	 * is the branch and shelving vocabulary of the configured systems rather than
	 * anything driven by user input, so the space is small and stable - the size cap is
	 * a backstop, not the mechanism. Expiry is what lets a location deleted by an
	 * operator be noticed again, and eviction costs one query.
	 * <p>
	 * It also carries the cost argument for the lookup being unindexed: the location
	 * table has no index but its primary key and holds branches rather than anything
	 * that grows with traffic, and this makes the read happen once per location instead
	 * of once per item.
	 */
	private final Cache<String, Boolean> recentlySeen = Caffeine.newBuilder()
		.maximumSize(10_000)
		.expireAfterWrite(Duration.ofHours(6))
		.build();

	/** Keyed on the id rather than the code, which an operator can rename. */
	private static String seenKey(DataHostLms hostSystem, String locationCode) {
		return hostSystem.getId() + "|" + locationCode;
	}

	/**
	 * The row to insert for a location DCB has just seen for the first time.
	 * <p>
	 * Built fresh rather than by setting fields on the reported location: that object
	 * belongs to an Item on the availability path and is about to be serialised into a
	 * response, so it is not ours to mutate. Building here also means only the fields
	 * named below reach the database, instead of whatever else an adapter happened to
	 * populate.
	 * <p>
	 * name and type are non-null in the schema and an item mapper reports little more
	 * than a code, so a discovered location stands for itself until somebody edits it.
	 */
	private static Location discovered(Location reported, DataAgency agency,
		DataHostLms hostSystem) {

		final var discovered = Location.builder()
			.id(UUIDUtils.generateLocationId(hostSystem.getCode(), reported.getCode()))
			.code(reported.getCode())
			.name(reported.getName() != null ? reported.getName() : reported.getCode())
			// DCB has seen a code on an item. Whether that is a branch, a campus or a
			// service point is not something it can tell.
			.type(reported.getType() != null ? reported.getType() : UNKNOWN_LOCATION_TYPE)
			// Null when the location did not map, which is what dynamicCreateLocation
			// reads to decide whether there is anything for a human to do.
			.agency(agency)
			.hostSystem(hostSystem)
			.build();

		// Assigned rather than built: activeWorkflows is @Singular, so the builder's
		// plural method rejects null, and a location reported by an item mapper has no
		// workflows at all.
		return discovered.setActiveWorkflows(reported.getActiveWorkflows());
	}

	private static final String UNKNOWN_LOCATION_TYPE = "UNKNOWN";

	/**
	 * Persist a location DCB discovered for itself, raising the review workflow when
	 * there is something for a human to do.
	 * <p>
	 * Only the unmapped ones are flagged. Every location an ILS reports arrives here
	 * the first time it is seen, and most of them on an established system already
	 * resolve to an agency perfectly well - marking those for attention too would bury
	 * the handful that actually need a mapping under everything that does not.
	 */
	private Mono<Location> dynamicCreateLocation(Location location) {
		if (location.getAgency() == null) {
			// Copied into a fresh map: Lombok's @Singular builder produces an immutable one
			Map<String, Workflow> activeWorkflows = location.getActiveWorkflows() == null
				? new HashMap<>()
				: new HashMap<>(location.getActiveWorkflows());

			activeWorkflows.put("DynamicLocation",
				Workflow.builder()
					.code("ReviewDynamicLocation")
					.status("PENDING")
					.build());

			location.setActiveWorkflows(activeWorkflows);
			location.setNeedsAttention(Boolean.TRUE);
		}

		return(Mono.from(locationRepository.save(location)));
	}
}

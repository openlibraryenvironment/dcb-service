package org.olf.dcb.core.interaction.polaris;

import static org.olf.dcb.core.interaction.polaris.PolarisConstants.*;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.polaris.exceptions.UnhandledItemStatusException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class PolarisItem {

	// A claim leaves the loan unresolved: nobody has seen the copy back, so it is still out
	private static final Set<String> CLAIMS = normalised(CLAIM_RETURNED, CLAIM_NEVER_HAD, CLAIM_MISSING_PARTS);

	/**
	 * Statuses only the owning library gives a copy it has in hand. On the return leg they mean the
	 * copy is back: the two MOBIUS requests a withdrawn copy left in RETURN_TRANSIT, and the in-repair,
	 * damaged and unavailable ones that waited until the copy read "In" again.
	 */
	private static final Set<String> HELD_BY_THE_OWNING_LIBRARY = normalised(WITHDRAWN, IN_REPAIR, DAMAGED,
		BINDERY, UNAVAILABLE, IN_PROCESS);

	// Documented Polaris statuses DCB has no state for, and passes through as Polaris spells them
	private static final Set<String> KNOWN_WITHOUT_A_DCB_STATE = Stream.of(HELD_BY_THE_OWNING_LIBRARY,
			normalised(ON_ORDER, ROUTED, RETURNED_ILL, E_CONTENT_EXTERNAL_LOAN))
		.flatMap(Set::stream)
		.collect(Collectors.toUnmodifiableSet());

	// Ref: https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/2669510868/DCB+Circulation+Lifecycle+Events
	private static final Map<Direction, Function<String, String>> STATUS_MAP = Map.of(
		Direction.POLARIS_TO_HOST_LMS, status -> switch (status) {
			case AVAILABLE,
				// for when returned by patron and checked back in
				SHELVING -> HostLmsItem.ITEM_AVAILABLE;
			// In transit to pickup location
			case TRANSFERRED,
				// In transit back to supplier
				IN_TRANSIT -> HostLmsItem.ITEM_TRANSIT;
			case ON_HOLD_SHELF -> HostLmsItem.ITEM_ON_HOLDSHELF;
			case CHECKED_OUT -> HostLmsItem.ITEM_LOANED;
			case LOST, MISSING -> HostLmsItem.ITEM_MISSING;
			default -> mapOtherStatus(status);
		},
		Direction.HOST_LMS_TO_POLARIS, status -> switch (status) {
			case HostLmsItem.ITEM_AVAILABLE -> AVAILABLE;
			case HostLmsItem.ITEM_TRANSIT -> TRANSFERRED;
			case HostLmsItem.ITEM_ON_HOLDSHELF -> ON_HOLD_SHELF;
			case HostLmsItem.ITEM_LOANED -> CHECKED_OUT;
			case HostLmsItem.ITEM_MISSING -> MISSING;
			default -> throw new UnhandledItemStatusException(
				"We do not currently map HostLmsItem status: " + status + " to a local item status.");
		}
	);

	/**
	 * A status DCB has no state of its own for is kept as Polaris spells it, so tracking records the
	 * change and no transition fires on it. Raising an error failed every tracking poll of the copy, and
	 * MISSING would stop tracking it for good, since a MISSING item is never read again.
	 */
	private static String mapOtherStatus(String status) {
		final var key = normalisedStatus(status);

		if (CLAIMS.contains(key)) {
			return HostLmsItem.ITEM_LOANED;
		}

		if (normalisedStatus(NON_CIRCULATING).equals(key)) {
			return HostLmsItem.LIBRARY_USE_ONLY;
		}

		if (!KNOWN_WITHOUT_A_DCB_STATE.contains(key)) {
			log.warn("Polaris item status '{}' is not a documented one; tracking keeps it as it is", status);
		}

		return status;
	}

	/** Whether DCB's last reading of a lent copy shows its owning library holding it. */
	static boolean isHeldByTheOwningLibrary(String itemStatus) {
		return HostLmsItem.LIBRARY_USE_ONLY.equals(itemStatus)
			|| HELD_BY_THE_OWNING_LIBRARY.contains(normalisedStatus(itemStatus));
	}

	static String mapItemStatus(Direction direction, String status) {
		return STATUS_MAP.getOrDefault(direction, dir -> {
			throw new IllegalArgumentException("Invalid direction: " + dir);
		}).apply(status);
	}

	// MOBIUS's Polaris sends "In-Repair" where the documentation writes "In Repair"
	private static String normalisedStatus(String status) {
		return status == null ? "" : status.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
	}

	private static Set<String> normalised(String... statuses) {
		return Stream.of(statuses).map(PolarisItem::normalisedStatus).collect(Collectors.toUnmodifiableSet());
	}
}

enum Direction {
	POLARIS_TO_HOST_LMS, HOST_LMS_TO_POLARIS, CRS_TO_POLARIS
}

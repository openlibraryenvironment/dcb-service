package org.olf.dcb.core.interaction.polaris;

import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.polaris.exceptions.UnknownItemStatusException;

import java.util.Map;
import java.util.function.Function;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.*;

@Slf4j
class PolarisItem {

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
			case MISSING -> HostLmsItem.ITEM_MISSING;
			default -> handleUnknownStatus(status);
		},
		Direction.HOST_LMS_TO_POLARIS, status -> switch (status) {
			case HostLmsItem.ITEM_AVAILABLE -> AVAILABLE;
			case HostLmsItem.ITEM_TRANSIT -> TRANSFERRED;
			case HostLmsItem.ITEM_ON_HOLDSHELF -> ON_HOLD_SHELF;
			case HostLmsItem.ITEM_LOANED -> CHECKED_OUT;
			case HostLmsItem.ITEM_MISSING -> MISSING;
			default -> throw new UnknownItemStatusException(
				"Unable to map a HostLmsItem status: '{ "+status+" }' to a Polaris item status.");
		}
	);

	private static String handleUnknownStatus(String status) {
		log.error("We don't have a mapping from local item status: '{}' to a HostLmsItem status.", status);

		throw new UnknownItemStatusException(
			"Cannot map Polaris local item status '{ "+status+" }' to a HostLmsItem status.");
	}

	static String mapItemStatus(Direction direction, String status) {
		return STATUS_MAP.getOrDefault(direction, dir -> {
			throw new IllegalArgumentException("Invalid direction: " + dir);
		}).apply(status);
	}
}

enum Direction {
	POLARIS_TO_HOST_LMS, HOST_LMS_TO_POLARIS, CRS_TO_POLARIS
}

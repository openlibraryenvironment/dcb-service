package org.olf.dcb.core.interaction.polaris;

import org.olf.dcb.core.interaction.HostLmsItem;

import java.util.Map;
import java.util.function.Function;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.*;

class PolarisItem {

	// Ref: https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/2669510868/DCB+Circulation+Lifecycle+Events
	private static final Map<Direction, Function<String, String>> STATUS_MAP = Map.of(
		Direction.POLARIS_TO_HOST_LMS, status -> switch (status) {
			case AVAILABLE -> HostLmsItem.ITEM_AVAILABLE;
			case TRANSFERRED -> HostLmsItem.ITEM_TRANSIT;
			case ON_HOLD_SHELF -> HostLmsItem.ITEM_ON_HOLDSHELF;
			case CHECKED_OUT -> HostLmsItem.ITEM_LOANED;
			case IN_TRANSIT -> HostLmsItem.ITEM_RETURNED;
			case MISSING -> HostLmsItem.ITEM_MISSING;
			default -> "UNKNOWN";
		},
		Direction.HOST_LMS_TO_POLARIS, status -> switch (status) {
			case HostLmsItem.ITEM_AVAILABLE -> AVAILABLE;
			case HostLmsItem.ITEM_TRANSIT -> TRANSFERRED;
			case HostLmsItem.ITEM_ON_HOLDSHELF -> ON_HOLD_SHELF;
			case HostLmsItem.ITEM_LOANED -> CHECKED_OUT;
			case HostLmsItem.ITEM_RETURNED -> IN_TRANSIT;
			case HostLmsItem.ITEM_MISSING -> MISSING;
			default -> throw new RuntimeException("Unable to map a HostLmsItem status: '{ "+status+" }' to a Polaris item status.");
		}
	);

	static String mapItemStatus(Direction direction, String status) {
		return STATUS_MAP.getOrDefault(direction, dir -> {
			throw new IllegalArgumentException("Invalid direction: " + dir);
		}).apply(status);
	}
}

enum Direction {
	POLARIS_TO_HOST_LMS, HOST_LMS_TO_POLARIS, CRS_TO_POLARIS
}

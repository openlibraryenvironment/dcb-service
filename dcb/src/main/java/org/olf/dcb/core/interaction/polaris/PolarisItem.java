package org.olf.dcb.core.interaction.polaris;

import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.polaris.exceptions.UnhandledItemStatusException;
import org.olf.dcb.core.interaction.polaris.exceptions.UnknownItemStatusException;
import org.olf.dcb.core.model.PatronRequest;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.*;

@Slf4j
class PolarisItem {

	// https://documentation.iii.com/polaris/7.4/PolarisStaffHelp/Patron_Services_Admin/PDPitems/Viewing_Circulation_Statuses.htm
	private static final List<String> unhandledLocalItemStatuses = List.of(
		CLAIM_RETURNED,
		CLAIM_NEVER_HAD,
		CLAIM_MISSING_PARTS,
		LOST,
		RETURNED_ILL,
		NON_CIRCULATING,
		WITHDRAWN,
		IN_REPAIR,
		BINDERY,
		UNAVAILABLE,
		IN_PROCESS,
		ON_ORDER,
		ROUTED,
		E_CONTENT_EXTERNAL_LOAN);

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
			default -> checkUnhandledStatus(status);
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

	private static String checkUnhandledStatus(String status) {
		log.error("We don't have a mapping from local item status: '{}' to a HostLmsItem status.", status);

		if (unhandledLocalItemStatuses.contains(status)) {
			throw new UnhandledItemStatusException("Local item status " + status + " is unhandled.");
		}

		throw new UnknownItemStatusException(
			"Local item status " + status + " is unknown.");
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

package org.olf.dcb.core.interaction;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@Serdeable
public record RequestShippingContext(
	int schemaVersion,
	String workflowCode,
	String routeMode,
	Patron patron,
	Endpoint borrowingLibrary,
	Endpoint supplier,
	PickupDestination pickupDestination,
	Provenance provenance
) {
	public static final int SCHEMA_VERSION = 1;
	public static final String DESTINATION_KIND = "PICKUP_LOCATION";

	public String shippingInstructions() {
		return "For pickup by %s@%s at %s:%s (%s); borrowing library %s:%s; route %s:%s -> %s:%s; workflow=%s"
			.formatted(
				patron.barcode(), patron.systemCode(),
				pickupDestination.owner().systemCode(), pickupDestination.localLocationCode(),
				pickupDestination.displayName(),
				borrowingLibrary.systemCode(), borrowingLibrary.agencyCode(),
				supplier.systemCode(), supplier.agencyCode(),
				pickupDestination.owner().systemCode(), pickupDestination.localLocationCode(),
				routeMode);
	}

	public String unstructuredAddress() {
		if (pickupDestination.address() != null && hasText(pickupDestination.address().formatted())) {
			return pickupDestination.displayName() + ", " + pickupDestination.address().formatted();
		}
		return "%s; system %s; agency %s; location %s"
			.formatted(
				pickupDestination.displayName(),
				pickupDestination.owner().systemCode(),
				pickupDestination.owner().agencyCode(),
				pickupDestination.localLocationCode());
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	@Serdeable
	public record Patron(String barcode, String systemCode, String agencyCode) {}

	@Serdeable
	public record Endpoint(String systemCode, String agencyCode, String agencyName) {}

	@Serdeable
	public record PickupDestination(
		String kind,
		Endpoint owner,
		String dcbLocationId,
		String dcbLocationCode,
		String localLocationCode,
		String displayName,
		AddressSnapshot address
	) {}

	@Serdeable
	public record AddressSnapshot(String formatted, String scope, String source) {}

	@Serdeable
	public record Provenance(
		String source,
		String selectedPickupValue,
		String selectedPickupCodeContext,
		String selectedPickupContext,
		Instant requestCreatedAt,
		Instant locationLastImportedAt
	) {}
}

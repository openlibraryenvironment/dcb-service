package org.olf.dcb.core.interaction;

/**
 * Whether a supplier can take a request, decided before a hold is placed there. Only
 * {@link Outcome#CANNOT_SUPPLY} stops a request; every other outcome lets it be placed.
 *
 * @param reason what is wrong, for the audit log and the request's error message
 * @param remedy what the library's staff need to do to fix it
 */
public record SupplierPreflight(Outcome outcome, String reason, String remedy) {
	public enum Outcome {
		CLEAR,
		NOT_CHECKED,
		// The supplier's configuration rules it out, and DCB cannot fix that itself
		CANNOT_SUPPLY,
		// DCB could not reach the supplier at its last check; the request is placed anyway
		UNREACHABLE
	}

	/** What a supplier is asked about: the request about to be placed there. */
	public record Query(String borrowingAgencyCode, String supplyingAgencyCode, String localItemType,
		String canonicalPatronType) { }

	public static SupplierPreflight clear() {
		return new SupplierPreflight(Outcome.CLEAR, null, null);
	}

	public static SupplierPreflight notChecked() {
		return new SupplierPreflight(Outcome.NOT_CHECKED, null, null);
	}

	public static SupplierPreflight cannotSupply(String reason, String remedy) {
		return new SupplierPreflight(Outcome.CANNOT_SUPPLY, reason, remedy);
	}

	public static SupplierPreflight unreachable(String reason) {
		return new SupplierPreflight(Outcome.UNREACHABLE, reason, null);
	}

	public boolean cannotSupply() {
		return outcome == Outcome.CANNOT_SUPPLY;
	}

	public boolean unreachable() {
		return outcome == Outcome.UNREACHABLE;
	}
}

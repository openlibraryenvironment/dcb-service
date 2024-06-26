package org.olf.dcb.request.fulfilment;

public enum SupplierRequestStatusCode {
	PENDING("PENDING"),
	PLACED("PLACED"),
	ACCEPTED("ACCEPTED"),

	CANCELLED("CANCELLED");

	private final String displayName;

	SupplierRequestStatusCode(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}

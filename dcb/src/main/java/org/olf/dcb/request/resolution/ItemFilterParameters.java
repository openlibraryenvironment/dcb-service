package org.olf.dcb.request.resolution;

import java.util.List;

public interface ItemFilterParameters {
	List<String> excludedSupplyingAgencyCodes();
	String borrowingAgencyCode();
	String borrowingHostLmsCode();
	String pickupAgencyCode();
	Boolean isExpeditedCheckout(); // To allow expedited checkout requests to proceed, and deny other supplier pickup use cases

	/** The volume a supplying copy must be, or null when any copy of the title will do */
	String requestedVolumeDesignation();
}

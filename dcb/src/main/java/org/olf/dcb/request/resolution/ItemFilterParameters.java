package org.olf.dcb.request.resolution;

import java.util.List;

public interface ItemFilterParameters {
	List<String> excludedSupplyingAgencyCodes();
	String borrowingAgencyCode();
	String borrowingHostLmsCode();
	String pickupAgencyCode();
}

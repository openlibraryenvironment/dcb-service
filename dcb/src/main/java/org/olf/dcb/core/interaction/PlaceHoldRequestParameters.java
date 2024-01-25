package org.olf.dcb.core.interaction;

import org.olf.dcb.core.model.Agency;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PlaceHoldRequestParameters {
	String localPatronId;
	String localPatronBarcode;
	String localPatronType;
	String localBibId;
	String localItemId;
	String localItemBarcode;
	String title;
	String canonicalItemType;
	String pickupLocation;
	Agency pickupAgency;
	String note;
	String patronRequestId;
	String supplyingAgencyCode;
	String supplyingLocalItemId;
	String supplyingLocalItemBarcode;

}

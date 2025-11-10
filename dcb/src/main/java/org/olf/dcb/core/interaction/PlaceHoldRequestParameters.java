package org.olf.dcb.core.interaction;

import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.Library;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PlaceHoldRequestParameters {
	String localPatronId;
	String localPatronBarcode;
	String localPatronType;
	String localBibId;
	String localHoldingId;
	String localItemId;
	String localItemBarcode;
	String title;
	String canonicalItemType;
	String pickupLocationCode;
	Agency pickupAgency;
	Location pickupLocation;
	Library pickupLibrary;
	String pickupNote;
	String note;
	String patronRequestId;
	String supplyingAgencyCode;
	String supplyingLocalBibId;
	String supplyingLocalItemId;
	String supplyingLocalItemBarcode;
	String supplyingLocalItemLocation;
	String activeWorkflow;
	String localNames;
}

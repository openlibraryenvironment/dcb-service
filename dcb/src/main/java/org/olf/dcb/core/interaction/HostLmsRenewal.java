package org.olf.dcb.core.interaction;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HostLmsRenewal {
	/** The ID of the item to renew */
	String localItemId;
	/** The Barcode of the item to renew */
	String localItemBarcode;
	/** The ID of the patron */
	String localPatronId;
	/** The BARCODE of the patron */
	String localPatronBarcode;
}

package org.olf.dcb.core.interaction;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HostLmsRenewal {
	/** The ID of the request associated with the loan to be renewed */
	String localRequestId;
	/** The ID of the item to renew */
	String localItemId;
	/** The barcode of the item to renew */
	String localItemBarcode;
	/** The ID of the patron */
	String localPatronId;
	/** The barcode of the patron */
	String localPatronBarcode;
}

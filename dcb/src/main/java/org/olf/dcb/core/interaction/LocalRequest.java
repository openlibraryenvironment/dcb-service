package org.olf.dcb.core.interaction;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LocalRequest {
	/** The ID of the request */
	String localId;

	/** The status of the request */
	String localStatus;

	/** Once known, the local id of the item actually requested */
	String heldItemId;

	/** Once known, the barcode of the item actually requested */
	String heldItemBarcode;
}

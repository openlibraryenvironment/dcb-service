package org.olf.dcb.core.interaction;

import io.micronaut.core.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LocalRequest {
	/** The ID of the request */
	String localId;

	/** The status of the request */
	String localStatus;

	/** The raw status of the request */
	String rawLocalStatus;

	/** Once known, the local id of the item actually requested */
	String requestedItemId;

	/** Once known, the barcode of the item actually requested */
	String requestedItemBarcode;

	/** Once known, the canonical item type  */
	@Nullable String canonicalItemType;

	/** Once known, the supplying agency code */
	@Nullable String supplyingAgencyCode;

	/** Once known, the suppliers host lms code */
	@Nullable String supplyingHostLmsCode;

	@Nullable String bibId;
	@Nullable String holdingId;
}

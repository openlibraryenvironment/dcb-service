package org.olf.dcb.request.workflow;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Serdeable
@Value
@Builder
public class PresentableItem {
	String localId;
	String barcode;
	String statusCode;
	Boolean requestable;
	String localItemType;
	String canonicalItemType;
	Integer holdCount;
	String agencyCode;
	String availableDate;
	String dueDate;
}

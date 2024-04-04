package org.olf.dcb.core.interaction.sierra;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;


@Value
@Builder
public class SierraItem {
	String id;
	String barcode;
	String callNumber;
	String statusCode;
	Instant dueDate;
	String locationName;
	String locationCode;
	String itemType;
	int holdCount;
	Boolean suppressed;
	Boolean deleted;
}

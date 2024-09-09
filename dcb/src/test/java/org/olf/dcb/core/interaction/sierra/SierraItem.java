package org.olf.dcb.core.interaction.sierra;

import java.time.Instant;
import java.util.Map;

import io.micronaut.core.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import services.k_int.interaction.sierra.FixedField;


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
	Map<Integer, FixedField> fixedFields;
}

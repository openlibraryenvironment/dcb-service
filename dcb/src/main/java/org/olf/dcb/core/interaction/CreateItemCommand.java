package org.olf.dcb.core.interaction;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import io.micronaut.core.annotation.Nullable;
import java.util.UUID;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class CreateItemCommand {

	// Added to enable tracking and reporting - this field isn't used functionally in the create item 
	// operation but it is useful for display when things go wrong.
	@Nullable
	UUID patronRequestId;

	@Nullable
	String bibId;

	@Nullable
	String locationCode;

	@Nullable
	String barcode;

	@Nullable
	String canonicalItemType;
}




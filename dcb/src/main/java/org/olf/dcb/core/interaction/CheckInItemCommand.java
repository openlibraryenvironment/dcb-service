package org.olf.dcb.core.interaction;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class CheckInItemCommand {
	@Nullable
	String itemId;
	@Nullable
	String localRequestId;
	@Nullable
	String itemBarcode;
	@Nullable
	String holdingId;
	@Nullable
	String bibId;
}




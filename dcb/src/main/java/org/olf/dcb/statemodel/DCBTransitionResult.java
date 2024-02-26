package org.olf.dcb.statemodel;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Serdeable
@ExcludeFromGeneratedCoverageReport
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class DCBTransitionResult {
	String resultCode;
	String resultState;
}

package org.olf.dcb.core.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;


@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class RecordCount {

	@NonNull
	private String value;

	@NonNull
	private Long count;
}

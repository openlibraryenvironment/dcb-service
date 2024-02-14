package org.olf.dcb.core.model;

import java.util.Map;
import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;


@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class RecordCountSummary {

	@NonNull
	private UUID sourceSystemId;

	@NonNull
	private String sourceSystemName;

	@NonNull
	private Long recordCount;
}

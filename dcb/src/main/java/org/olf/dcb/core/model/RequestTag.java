package org.olf.dcb.core.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

/** One entry in the consortium's vocabulary of patron request tags. */
@Data
@Accessors(chain = true)
@Serdeable
@ExcludeFromGeneratedCoverageReport
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
@MappedEntity
public class RequestTag {
	@NonNull
	@NotNull
	@Id
	private String code;

	@NonNull
	@NotNull
	private String label;

	@Nullable
	private String description;

	@NonNull
	@NotNull
	private Boolean active;
}

package org.olf.dcb.ingest.job;

import java.time.Instant;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Data
@Value
@AllArgsConstructor(onConstructor_ = @Creator())
@Builder
@Serdeable
public class IngestJobParams {
	
	@Nullable
	final Instant lastFetchTime;
	
	@NonNull
	@NotNull
	final int pageSize;
}

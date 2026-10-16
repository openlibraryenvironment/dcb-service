package org.olf.dcb.api;

import java.util.List;
import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

/** The batch availability response, as a client sees it. */
@Data
@Serdeable
public class BatchAvailabilityResponse {
	@Nullable
	private final List<AvailabilityResponse> results;
	@Nullable
	private final List<Unchecked> failures;

	@Data
	@Serdeable
	public static class Unchecked {
		@Nullable
		private final UUID clusteredBibId;
		@Nullable
		private final String message;
	}
}

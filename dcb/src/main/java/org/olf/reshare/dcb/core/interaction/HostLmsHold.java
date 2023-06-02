package org.olf.reshare.dcb.core.interaction;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import io.micronaut.core.annotation.Nullable;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class HostLmsHold {

        // Likely that the hold has been deleted from the host system
        public static final String HOLD_MISSING="MISSING";

        // The Hold has been placed, but is not yet available
        public static final String HOLD_PLACED="PLACED";

        // The Hold is Ready for pickup
        public static final String HOLD_READY="READY";

        // The hold is in transit
        public static final String HOLD_TRANSIT="TRANSIT";

	@Nullable
	String localId;

	@Nullable
	String status;
}

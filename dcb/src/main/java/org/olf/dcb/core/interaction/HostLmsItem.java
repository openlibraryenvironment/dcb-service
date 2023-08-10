package org.olf.dcb.core.interaction;

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
public class HostLmsItem {

        // Likely that the hold has been deleted from the host system
        public static final String ITEM_MISSING="MISSING";

        // The Hold has been placed, but is not yet available
        public static final String ITEM_AVAILABLE="AVAILABLE";

        // The hold is in transit
        public static final String ITEM_TRANSIT="TRANSIT";

        // Item is off-site
        public static final String ITEM_OFFSITE="OFFSITE";
				// Item on hold shelf
				public static final String ITEM_ON_HOLDSHELF="HOLDSHELF";
        // Item is off-site
        public static final String ITEM_LOANED="LOANED";

	String localId;
	String status;
	String barcode;
}

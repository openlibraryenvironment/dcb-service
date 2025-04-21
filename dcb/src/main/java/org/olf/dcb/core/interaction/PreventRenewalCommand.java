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
public class PreventRenewalCommand {

	// For folio we need access to the request ID rather than the item id.
	private String requestId;

  // Barcode of the virtual item at the borrowing system
	private String itemBarcode;

	// ItemID of the virtual item at the borrowing system
	private String itemId;

}

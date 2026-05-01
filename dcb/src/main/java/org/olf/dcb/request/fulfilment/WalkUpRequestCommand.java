package org.olf.dcb.request.fulfilment;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Serdeable
@Builder
@Value
public class WalkUpRequestCommand {
	@NonNull String itemHostLmsCode;
	@NonNull String itemAgencyCode; // Auto-filled for the user (must be their agency)
	@NonNull String itemBarcode;
	@NonNull String pickupLocationCode; // Needed to satisfy standard request payload
	@NonNull String patronBarcode;
	@NonNull String patronLocalId;
	@NonNull String patronAgencyCode;
	@NonNull String patronHostLmsCode;
}

package org.olf.dcb.request.fulfilment;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

@Serdeable
@Introspected
@Builder
@Value
public class WalkUpRequestCommand {
	@NonNull @NotBlank String itemHostLmsCode;
	@NonNull @NotBlank String itemAgencyCode;
	@NonNull @NotBlank String itemBarcode;
	@NonNull @NotBlank String pickupLocationCode;
	@NonNull @NotBlank String patronLocalId;
	@NonNull @NotBlank String patronAgencyCode;
	@NonNull @NotBlank String patronHostLmsCode;
}

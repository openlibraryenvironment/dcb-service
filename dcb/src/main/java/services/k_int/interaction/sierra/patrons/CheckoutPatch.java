package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

import io.micronaut.core.annotation.Nullable;

@Data
@Serdeable
@Builder
public class CheckoutPatch {
        String itemBarcode;
        String patronBarcode;
				@Nullable String patronPin;
}

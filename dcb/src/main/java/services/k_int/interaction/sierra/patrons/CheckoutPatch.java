package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import io.micronaut.core.annotation.Nullable;

@Data
@Serdeable
@Builder
public class CheckoutPatch {
        String itemBarcode;
        @ToString.Exclude
        String patronBarcode;
				@ToString.Exclude
				@Nullable String patronPin;
}

package services.k_int.interaction.sierra.patrons;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Serdeable
@Data
public class FixedField {
        @Nullable
        String label;
        @Nullable
        Object value;
        @Nullable
        String display;
}


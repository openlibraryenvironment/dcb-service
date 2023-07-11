package services.k_int.interaction.sierra;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.Builder;

@Builder
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


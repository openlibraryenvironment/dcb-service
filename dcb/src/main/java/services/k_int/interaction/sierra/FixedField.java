package services.k_int.interaction.sierra;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.Builder;

@Builder
@Serdeable
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FixedField {
        @Nullable
        String label;
        @Nullable
        Object value;
        @Nullable
        String display;
}


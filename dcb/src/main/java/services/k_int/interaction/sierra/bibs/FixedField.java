package services.k_int.interaction.sierra.bibs;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Serdeable
@Builder
public class FixedField {
        String label;
        Object value;
}


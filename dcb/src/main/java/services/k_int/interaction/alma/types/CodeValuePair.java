package services.k_int.interaction.alma.types;

import io.micronaut.serde.annotation.Serdeable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class CodeValuePair {
        String value;
        String desc;
}

package services.k_int.interaction.sierra.configuration;


import java.util.List;
import java.util.Map;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record PatronMetadata (
    String field,
    List<Map<String,Object>> values) {
}

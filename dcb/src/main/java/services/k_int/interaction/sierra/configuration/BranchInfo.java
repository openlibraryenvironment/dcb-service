package services.k_int.interaction.sierra.configuration;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record BranchInfo(
        @NotEmpty String id,
        @NotEmpty String name,
        @Nullable String address,
        @Nullable String emailSource,
        @Nullable String emailReplyTo,
        @Nullable String latitude,
        @Nullable String longitude,
        @Nullable List<Map<String,String>> locations
        ) {

}


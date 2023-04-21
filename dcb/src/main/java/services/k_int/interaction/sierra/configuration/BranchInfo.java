package services.k_int.interaction.sierra.configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@Serdeable
public record BranchInfo(
        @NotEmpty String id,
        @NotEmpty String name,
        @Nullable String address,
        @Nullable String emailSource,
        @Nullable String emailReplyTo,
        @Nullable String latitude,
        @Nullable String longitude,
        @Nullable List<Map> locations
        ) {

}


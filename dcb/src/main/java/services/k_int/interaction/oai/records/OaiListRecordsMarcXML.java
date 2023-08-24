package services.k_int.interaction.oai.records;


import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.marc4j.marc.Record;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record OaiListRecordsMarcXML (
        @NotEmpty String id,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        @Nullable LocalDateTime updatedDate,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        @Nullable LocalDateTime createdDate,
        @JsonFormat(pattern = "yyyy-MM-dd")
        @Nullable LocalDate deletedDate,
        @NotNull boolean deleted,
        @Nullable Boolean suppressed,
        @Nullable Record marc) {

}

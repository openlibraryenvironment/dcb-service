package services.k_int.interaction.sierra.bibs;

import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record BibResult(
	@NotEmpty String id,
	
	@JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss'Z'")
	@Nullable LocalDateTime updatedDate,
	
	@JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss'Z'")
	@Nullable LocalDateTime createdDate,
	
	@JsonFormat(pattern="yyyy-MM-dd")
	@Nullable LocalDate deletedDate,
	
	@NotNull boolean deleted,
	@Nullable String title) {

}

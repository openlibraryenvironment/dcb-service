package services.k_int.interaction.sierra.items;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Serdeable
@Builder
public class ResultSet {
	int total;
	int start;
	@NotNull List<SierraItem> entries;
}

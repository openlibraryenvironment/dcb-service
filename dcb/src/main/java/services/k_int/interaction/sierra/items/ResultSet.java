package services.k_int.interaction.sierra.items;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import services.k_int.interaction.sierra.SierraError;

import javax.validation.constraints.NotNull;
import java.util.List;
@Data
@Serdeable
public class ResultSet extends SierraError {
	int total;
	int start;
	@NotNull List<Result> entries;
}

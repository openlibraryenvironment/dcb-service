package services.k_int.interaction.sierra.items;

import java.util.List;

import javax.validation.constraints.NotNull;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
@Data
@Serdeable
public class ResultSet {
	int total;
	int start;
	@NotNull List<Result> entries;
}

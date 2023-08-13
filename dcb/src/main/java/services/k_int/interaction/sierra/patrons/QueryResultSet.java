package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import services.k_int.interaction.sierra.LinkResult;

@Data
@Serdeable
public class QueryResultSet {
	private Integer total;
	private Integer start;
	private LinkResult[] entries;
}

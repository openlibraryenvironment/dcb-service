package services.k_int.interaction.sierra;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@Serdeable
public class QueryResultSet {

	@JsonProperty("total")
	private int total;

	@JsonProperty("start")
	private int start;

	@JsonProperty("entries")
	private List<Entry> entries;

	@Data
	@Builder
	@AllArgsConstructor
	@Serdeable
	public static class Entry {

		@JsonProperty("link")
		private String link;
	}
}

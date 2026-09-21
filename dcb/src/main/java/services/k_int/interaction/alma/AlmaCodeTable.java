package services.k_int.interaction.alma;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_code_table.xsd
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Serdeable
public class AlmaCodeTable {
	@JsonProperty("name")
	String name;

	@JsonProperty("description")
	String description;

	@JsonProperty("row")
	List<Row> rows;

	@Data
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	@Serdeable
	public static class Row {
		@JsonProperty("code")
		String code;

		@JsonProperty("description")
		String description;

		@JsonProperty("enabled")
		Boolean enabled;
	}
}

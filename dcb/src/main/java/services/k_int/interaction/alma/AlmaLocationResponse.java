package services.k_int.interaction.alma;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Serdeable
public class AlmaLocationResponse {
	@JsonProperty("location")
	List<AlmaLocation> locations;

	@JsonProperty("total_record_count")
	int totalRecordCount;
}

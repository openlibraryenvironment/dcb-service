package services.k_int.interaction.alma.types.userRequest;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;


@Data
@AllArgsConstructor
@Builder
@Serdeable
public class AlmaRequests {
	@JsonProperty("total_record_count")
	Integer recordCount;
	@JsonProperty("requests")
	List<AlmaRequest> requests;
}

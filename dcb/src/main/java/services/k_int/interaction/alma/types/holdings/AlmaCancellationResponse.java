package services.k_int.interaction.alma.types.holdings;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import services.k_int.interaction.alma.types.CodeValuePair;

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class AlmaCancellationResponse {
	@JsonProperty("request_id")
	String requestId;
	@JsonProperty("status")
	CodeValuePair status;
	@JsonProperty("reason")
	String reason;
	@JsonProperty("user_id")
	String userId;
	@JsonProperty("item_barcode")
	String itemBarcode;

}

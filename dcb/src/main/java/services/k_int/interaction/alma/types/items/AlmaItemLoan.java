package services.k_int.interaction.alma.types.items;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.interaction.alma.types.CodeValuePair;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Serdeable
public class AlmaItemLoan {

	@JsonProperty("library")
	private CodeValuePair library;

	@JsonProperty("circ_desk")
	private CodeValuePair circDesk;

	@JsonProperty("return_circ_desk")
	private CodeValuePair returnCircDesk;

	@JsonProperty("request_id")
	private CodeValuePair requestId;
}

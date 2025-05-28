package services.k_int.interaction.alma;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.interaction.alma.types.CodeValuePair;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Serdeable
public class AlmaLocation {
	@JsonProperty("accession_placement")
	CodeValuePair accessionPlacement;

	@JsonProperty("call_number_type")
	CodeValuePair callNumberType;

	@JsonProperty("circ_desk")
	List<AlmaCircDesk> circDesk;

	String code;

	@JsonProperty("external_name")
	String externalName;

	@JsonProperty("fulfillment_unit")
	CodeValuePair fulfillmentUnit;

	String link;
	String map;
	String name;

	@JsonProperty("remote_storage")
	String remoteStorage;

	@JsonProperty("suppress_from_publishing")
	String suppressFromPublishing;

	CodeValuePair type;

	// DCB added fields so we can understand the library this location is at
	// will only be set manually
	@Nullable String libraryCode;
	@Nullable String libraryName;
}

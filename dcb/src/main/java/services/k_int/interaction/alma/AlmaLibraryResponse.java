package services.k_int.interaction.alma;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.LinkValuePair;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Serdeable
public class AlmaLibraryResponse {
	CodeValuePair campus;
	String code;

	@JsonProperty("default_location")
	CodeValuePair defaultLocation;

	String id;
	String link;
	String name;

	@JsonProperty("number_of_circ_desks")
	LinkValuePair numberOfCircDesks;

	@JsonProperty("number_of_locations")
	LinkValuePair numberOfLocations;

	String path;
	String proxy;

	@JsonProperty("resource_sharing")
	boolean resourceSharing;

	String description; // Optional, only appears in some entries
}

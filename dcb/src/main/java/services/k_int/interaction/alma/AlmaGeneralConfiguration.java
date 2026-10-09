package services.k_int.interaction.alma;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.interaction.alma.types.CodeValuePair;

/** The two fields of GET /almaws/v1/conf/general a ping reports: which institution, and which environment. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Serdeable
public class AlmaGeneralConfiguration {
	CodeValuePair institution;

	@JsonProperty("environment_type")
	String environmentType;
}

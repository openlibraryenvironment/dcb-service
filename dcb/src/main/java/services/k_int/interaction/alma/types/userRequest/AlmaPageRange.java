package services.k_int.interaction.alma.types.userRequest;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import services.k_int.interaction.alma.types.WithAttr;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.AlmaBib;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd
// https://developers.exlibrisgroup.com/alma/apis/bibs/

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class AlmaPageRange {
	@JsonProperty("from_page")
	String fromPage; 
  @JsonProperty("to_page")
	String toPage;
}

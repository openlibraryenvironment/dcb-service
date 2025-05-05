package services.k_int.interaction.alma.types.holdings;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import services.k_int.interaction.alma.types.CodeValuePair;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd
// https://developers.exlibrisgroup.com/alma/apis/bibs/

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class AlmaHolding {
	@JsonProperty("holding_id")
	String holdingId;
	@JsonProperty("library")
	CodeValuePair library;
	@JsonProperty("location")
	CodeValuePair location;
	@JsonProperty("call_number")
	String call_number;
	@JsonProperty("accession_number")
	String accessionNumber;
	@JsonProperty("suppress_from_publishing")
	String suppressFromPublishing;
}

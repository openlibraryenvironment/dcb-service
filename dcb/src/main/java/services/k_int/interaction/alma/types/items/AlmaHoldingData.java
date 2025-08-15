package services.k_int.interaction.alma.types.items;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.WithAttr;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd
// https://developers.exlibrisgroup.com/alma/apis/bibs/

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class AlmaHoldingData {
	@JsonProperty("link")
	String link;
	@JsonProperty("holding_id")
	String holdingId;
	@JsonProperty("copy_id")
	String copyId;
	@JsonProperty("in_temp_location")
	Boolean inTempLocation;
	@JsonProperty("temp_library")
	WithAttr tempLibrary;
	@JsonProperty("temp_location")
	WithAttr tempLocation;
	@JsonProperty("temp_call_number_type")
	WithAttr tempCallNumberType;
	@JsonProperty("temp_call_number")
	String tempCallNumber;
	@JsonProperty("call_number")
	String callNumber;
	@JsonProperty("temp_call_number_source")
	String tempCallNumberSource;
	@JsonProperty("temp_policy")
	WithAttr tempPolicy;
	@JsonProperty("due_back_date")
	String dueBackDate;
}

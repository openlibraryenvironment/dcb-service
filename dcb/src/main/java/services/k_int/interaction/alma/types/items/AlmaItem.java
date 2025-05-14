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
import services.k_int.interaction.alma.types.AlmaBib;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd
// https://developers.exlibrisgroup.com/alma/apis/bibs/

@Data
@AllArgsConstructor
@Builder
@Serdeable
public class AlmaItem {
	@JsonProperty("link")
	String link;
  @JsonProperty("bib_data")
	AlmaBib bibData;
  @JsonProperty("holding_data")
	AlmaHoldingData holdingData;
  @JsonProperty("item_data")
	AlmaItemData itemData;
}

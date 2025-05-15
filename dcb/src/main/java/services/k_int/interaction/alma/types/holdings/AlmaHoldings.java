package services.k_int.interaction.alma.types.holdings;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd
// https://developers.exlibrisgroup.com/alma/apis/bibs/

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class AlmaHoldings {

	@JsonProperty("total_record_count")
	Integer totalRecordCount;

	@JsonProperty("holding")
	List<AlmaHolding> holdings;

	@JsonProperty("bib_data")
	AlmaBibData bibData;

}


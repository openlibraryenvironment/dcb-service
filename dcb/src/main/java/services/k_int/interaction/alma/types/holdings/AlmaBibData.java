package services.k_int.interaction.alma.types.holdings;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd
// https://developers.exlibrisgroup.com/alma/apis/bibs/

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd
// https://developers.exlibrisgroup.com/alma/apis/bibs/

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class AlmaBibData {
	@JsonProperty("mms_id")
	String mmsId;
	@JsonProperty("title")
	String title;
	@JsonProperty("author")
	String author;
	@JsonProperty("isbn")
	String isbn;
	@JsonProperty("issn")
	String issn;
	@JsonProperty("complete_edition")
	String completeEdition;
	@JsonProperty("network_numbers")
	List<String> number;
	@JsonProperty("place_of_publication")
	String placeOfPublication;
	@JsonProperty("publisher")
	String publisher;
}


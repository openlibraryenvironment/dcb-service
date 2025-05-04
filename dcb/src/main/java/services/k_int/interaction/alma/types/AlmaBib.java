package services.k_int.interaction.alma.types;

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
public class AlmaBib {
	@JsonProperty("mms_id")
	String mmsId;
	@JsonProperty("title")
	String title;
	@JsonProperty("author")
	String author;
	@JsonProperty("publisher")
	String publisher;
	@JsonProperty("publication_date")
	String publicationDate;
	@JsonProperty("material_type")
	CodeValuePair materialType;
	@JsonProperty("language")
	String language;
	@JsonProperty("isbn")
	String isbn;
	@JsonProperty("issn")
	String issn;
	@JsonProperty("call_number")
	String callNumber;
	@JsonProperty("collections")
	List<CodeValuePair> collections;
	@JsonProperty("suppress_from_publishing")
	String suppressFromPublishing;
	@JsonProperty("suppress_from_external_search")
	String suppressFromExternalSearch;
	@JsonProperty("suppress_from_metadoor")
	String suppressFromMetadoor;
}


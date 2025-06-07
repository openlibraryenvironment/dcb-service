package services.k_int.interaction.oaipmh;

import java.time.Instant;
import org.marc4j.marc.Record;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@Serdeable
public record OaiRecord (
		
	Header header,
	Metadata metadata) {
	
	@Serdeable
	public static record Metadata(
			Record record
			) {
	}
	
	@Serdeable
	public static record Header(
		String identifier,
		Instant datestamp,
		// Note: As this property is called setSpec this causes an issue for the object mapper as it removed the "set"
		// and looks for a property called "spec", hence we need to specify it as "spec" in the JsonProperty
		@JsonProperty("spec")
		String setSpec,

    @JsonProperty
    @JacksonXmlProperty(isAttribute = true)
    String status

	) {
	}
}

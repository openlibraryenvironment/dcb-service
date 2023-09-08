package services.k_int.interaction.oaipmh;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ListRecordsResponse(
		String resumptionToken,
		
		@JacksonXmlElementWrapper(useWrapping = false)
		@JsonProperty("record")
		List<OaiRecord> records
		) {
	
	
	@Serdeable
	public static record ResumptionToken (
			
			// @JacksonXmlText Doesn't work here, so we use the functional equivalent of the empty
			// string property name.
			@JsonProperty("")
			String value,
			
			Instant expirationDate,
			Integer completeListSize,
			Integer cursor
			) {
	}
}

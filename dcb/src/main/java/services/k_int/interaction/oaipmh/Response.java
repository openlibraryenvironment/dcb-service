package services.k_int.interaction.oaipmh;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Response (		
	Instant responseDate,
	Request request,
	Error error,
	
	@JsonProperty("ListRecords")
	ListRecordsResponse listRecords
	) {
	
	@Serdeable
	public static enum ErrorCode {
		cannotDisseminateFormat,
		idDoesNotExist,
		badArgument,
		badVerb,
		noMetadataFormats,
		noRecordsMatch,
		badResumptionToken,
		noSetHierarchy
	}
	
	@Serdeable
	public static record Error (
			
			// @JacksonXmlText Doesn't work here, so we use the functional equivalent of the empty
			// string property name.
			@JsonProperty("")
			String detail,
			
			ErrorCode code
			) {
	}
	
	@Serdeable
	public static record Request (
		String verb,
		String metadataPrefix 
		) {
		
	}
}

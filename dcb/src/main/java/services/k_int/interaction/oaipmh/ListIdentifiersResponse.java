package services.k_int.interaction.oaipmh;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import io.micronaut.serde.annotation.Serdeable;
import services.k_int.interaction.oaipmh.OaiRecord.Header;

@Serdeable
public record ListIdentifiersResponse(
		String resumptionToken,

		@JacksonXmlElementWrapper(useWrapping = false)
		@JsonProperty("header")
		List<Header> headers
		) {
}

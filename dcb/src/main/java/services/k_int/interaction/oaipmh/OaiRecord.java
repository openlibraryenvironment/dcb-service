package services.k_int.interaction.oaipmh;

import java.time.Instant;

import org.marc4j.marc.Record;

import io.micronaut.serde.annotation.Serdeable;

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
	public static record Header(String identifier, Instant datestamp, String setSpec) {
	}
}

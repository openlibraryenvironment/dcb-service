package services.k_int.interaction.oaipmh;

import java.time.Instant;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Builder(toBuilder = true)
@Data
@Serdeable
public class ListRecordsParams {
	private final String verb = "ListRecords";
	private final Instant from;
	private final Instant until;
	private final String resumptionToken;
	private final String metadataPrefix;
	private final String set;
	private final String cpType;
	private final String hostCode;
	private final Instant highestRecordTimestampSeen;
}

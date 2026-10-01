package services.k_int.interaction.oaipmh;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record GetRecordResponse(
		OaiRecord record
		) {
}

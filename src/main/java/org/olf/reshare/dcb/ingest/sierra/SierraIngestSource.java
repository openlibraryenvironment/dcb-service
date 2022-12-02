package org.olf.reshare.dcb.ingest.sierra;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.time.Instant;
import java.util.UUID;

import org.olf.reshare.dcb.ingest.IngestRecord;
import org.olf.reshare.dcb.ingest.IngestRecordBuilder;
import org.olf.reshare.dcb.ingest.IngestSource;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import services.k_int.interaction.sierra.BibRecord;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.utils.UUIDUtils;

@Singleton
@Requires(property = (SierraIngestSource.CONFIG_ROOT + ".enabled"), value = "true")
public class SierraIngestSource implements IngestSource {

	public static final String CONFIG_ROOT = "sierra.ingest";

	private static Logger log = LoggerFactory.getLogger(SierraIngestSource.class);
	public static final UUID NAMESPACE = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
			SierraIngestSource.class.getSimpleName());

	private final SierraApiClient sierraApi;

	SierraIngestSource(SierraApiClient sierraApi) {
		this.sierraApi = sierraApi;
	}

	@Override
	public Publisher<IngestRecord> apply(Instant since) {
		
		log.info("Fecthing from Sierra");

		// The stream of imported records.
		return Flux.from(getAllResults())
				.filter( sierraBib ->  sierraBib.title() != null )
				.map(sierraBib -> IngestRecordBuilder.builder()
						.uuid(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE, sierraBib.id()))
						.title(sierraBib.title())
						.build());
	}

	private Publisher <BibRecord> getAllResults() {
		log.info("Fetching scroll page from Sierra");
		return Flux.from(sierraApi.bibs())
				.flatMap( response -> Flux.fromIterable(response.entries()) );
	}
}

package org.olf.reshare.dcb.ingest.sierra;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraBibRecord;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.SierraBibParams;
import services.k_int.interaction.sierra.SierraDateTimeRange;
import services.k_int.utils.UUIDUtils;

@Singleton
@Requires(property = (SierraIngestSource.CONFIG_ROOT + ".enabled"), value = "true", defaultValue = "false")
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
		
		log.info("Fetching from Sierra");

		// The stream of imported records.
		return Flux.from(scrollAllResults(since, 0, 2000))
				.filter( sierraBib -> sierraBib.title() != null )
				.switchIfEmpty(
						Flux.just("No results returned. Stopping")
							.mapNotNull(s -> {
								log.info(s);
								return null;
							}))
				.map(sierraBib -> IngestRecordBuilder.builder()
						.uuid(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE, sierraBib.id()))
						.title(sierraBib.title())
						.build());
	}
	
	private Publisher<SierraBibRecord> scrollAllResults(final Instant since, final int offset, final int limit) {
		log.info("Fetching batch from Sierra API");

		final SierraBibParams apiParams = SierraBibParams.build(params -> {
			params
				.deleted(false)
				.offset(offset)
				.limit(limit);
			
			if (since != null) {

				params
					.updatedDate(SierraDateTimeRange.build(dtr -> {
						dtr
							.to(LocalDateTime.now())
							.from(LocalDateTime.from(since));
					}));
			}
		});

		return Mono.from(sierraApi.bibs(apiParams)).flatMapMany(resp -> {

			final List<SierraBibRecord> bibs = resp.entries();
			log.info("Fetched a chunk of {} records", bibs.size());
			final int nextOffset = resp.start() + bibs.size();
			final boolean possiblyMore = bibs.size() == limit;

			if (!possiblyMore) {
				log.info("No more results to fetch");
			}

			final Flux<SierraBibRecord> currentPage = Flux.fromIterable(bibs);
			
			// Try next page if there is the possibility of more results.
			return possiblyMore ? Flux.concat(currentPage, scrollAllResults(since, nextOffset, limit)) : currentPage;
		});
	}
}

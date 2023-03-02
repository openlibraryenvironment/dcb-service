package org.olf.reshare.dcb.ingest.sierra;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import org.marc4j.marc.Record;
import org.olf.reshare.dcb.ingest.marc.MarcIngestSource;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.bibs.BibResult;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.utils.UUIDUtils;

@Singleton
@Requires(property = (SierraIngestSource.CONFIG_ROOT + ".enabled"), value = "true", defaultValue = "false")
public class SierraIngestSource implements MarcIngestSource<BibResult> {

	public static final String CONFIG_ROOT = "sierra.ingest";

	private static Logger log = LoggerFactory.getLogger(SierraIngestSource.class);

	private final SierraApiClient sierraApi;

	SierraIngestSource(SierraApiClient sierraApi) {
		this.sierraApi = sierraApi;
	}	
	
	private Mono<BibResultSet> fetchPage (Instant since, int offset, int limit) {
		log.info("Fetching batch from Sierra API with since={} offset={} limit={}", since, offset, limit);
		return Mono.from(sierraApi.bibs(params -> {
			params
				.deleted(false)
				.offset(offset)
				.limit(limit)
				.fields(List.of(
					"id", "updatedDate", "createdDate",
					"deletedDate", "deleted", "marc"));

			if (since != null) {
				params
					.updatedDate(dtr -> {
						dtr
							.to(LocalDateTime.now())
							.fromDate(LocalDateTime.from(since));
					});
			}
		}));
	}
	
	private Publisher<BibResult> pageAllResults(Instant since, int offset, int limit) {
		
		return fetchPage(since, offset, limit)
			.expand(results -> {
				var bibs = results.entries();
				
				log.info("Fetched a chunk of {} records", bibs.size());
				final int nextOffset = results.start() + bibs.size();
				final boolean possiblyMore = bibs.size() == limit;
				
				if (!possiblyMore) {
					log.info("No more results to fetch");
					return Mono.empty();
				}
				
				return fetchPage(since, nextOffset, limit);
			})
			
			.concatMap(results -> Flux.fromIterable( new ArrayList<>( results.entries() )));
	}

	@Override
	@NotNull
	public String getDefaultControlIdNamespace() {
		return "sierra";
	}

	@Override
	public Publisher<BibResult> getResources(Instant since) {
		log.info("Fetching MARC JSON from Sierra");

		// The stream of imported records.
		return Flux.from(pageAllResults(since, 0, 2000))
			.filter(sierraBib -> sierraBib.marc() != null)
			.switchIfEmpty(
				Mono.just("No results returned. Stopping")
					.mapNotNull(s -> {
						log.info(s);
						return null;
					}));
	}

	@Override
	public IngestRecordBuilder initIngestRecordBuilder(BibResult resource) {
		return IngestRecord.builder()
			.uuid(uuid5ForBibResult(resource));
	}
	
	private static final String UUID5_PREFIX = "ingest-source:sierra";
	
	public UUID uuid5ForBibResult( @NotNull final BibResult result ) {
		
		final String concat = UUID5_PREFIX + ":" + result.id();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public Record resourceToMarc(BibResult resource) {
		return resource.marc();
	}

	@Override
	public String getName() {
		return this.getClass().getName();
	}
}

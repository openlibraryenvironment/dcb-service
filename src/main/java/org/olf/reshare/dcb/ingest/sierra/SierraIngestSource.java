package org.olf.reshare.dcb.ingest.sierra;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.marc4j.marc.Record;
import org.olf.reshare.dcb.ingest.marc.MarcIngestSource;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord.Builder;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.bibs.BibResult;

@Singleton
@Requires(property = (SierraIngestSource.CONFIG_ROOT + ".enabled"), value = "true", defaultValue = "false")
public class SierraIngestSource extends MarcIngestSource<BibResult> {

	public static final String CONFIG_ROOT = "sierra.ingest";

	private static Logger log = LoggerFactory.getLogger(SierraIngestSource.class);

	private final SierraApiClient sierraApi;

	SierraIngestSource(SierraApiClient sierraApi) {
		this.sierraApi = sierraApi;
	}
	
	private Publisher<BibResult> scrollAllResults(final Instant since, final int offset, final int limit) {
		log.info("Fetching batch from Sierra API");

		return Mono.from(sierraApi.bibs(params -> {
			params
				.deleted(false)
				.offset(offset)
				.limit(limit)
				.addFields(
						"id", "updatedDate", "createdDate",
						"deletedDate", "deleted", "marc");
			
			if (since != null) {
				params
					.updatedDate(dtr -> {
						dtr
							.to(LocalDateTime.now())
							.from(LocalDateTime.from(since));
					});
			}
		}))
			.flatMapMany(resp -> {
	
				final List<BibResult> bibs = resp.entries();
				log.info("Fetched a chunk of {} records", bibs.size());
				final int nextOffset = resp.start() + bibs.size();
				final boolean possiblyMore = bibs.size() == limit;
	
				if (!possiblyMore) {
					log.info("No more results to fetch");
				}
	
				final Flux<BibResult> currentPage = Flux.fromIterable(bibs);
				
				// Try next page if there is the possibility of more results.
				return possiblyMore ? Flux.concat(currentPage, scrollAllResults(since, nextOffset, limit)) : currentPage;
			});
	}

	@Override
	protected @NotNull String getDefaultControlIdNamespace() {
		return "sierra";
	}

	@Override
	protected Publisher<BibResult> getResources(Instant since) {
		log.info("Fetching MARC JSON from Sierra");
		
		// The stream of imported records.
		return Flux.from(scrollAllResults(since, 0, 2000))
			.filter( sierraBib -> sierraBib.marc() != null )
			.switchIfEmpty(
					Mono.just("No results returned. Stopping")
						.mapNotNull(s -> {
							log.info(s);
							return null;
						}));
	}

	@Override
	protected Builder initIngestRecordBuilder(BibResult resource) {
		return IngestRecord.builder()
			.uuid(getUUID5ForId(resource.id()));
	}

	@Override
	protected Record resourceToMarc(BibResult resource) {
		return resource.marc();
	}
}

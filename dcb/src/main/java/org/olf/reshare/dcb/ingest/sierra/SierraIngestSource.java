package org.olf.reshare.dcb.ingest.sierra;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import org.marc4j.marc.Record;
import org.olf.reshare.dcb.ingest.marc.MarcIngestSource;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.olf.reshare.dcb.ingest.model.RawSource;
import org.olf.reshare.dcb.storage.RawSourceRepository;
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

//@Singleton
//@Requires(property = (SierraIngestSource.CONFIG_ROOT + ".enabled"), value = "true", defaultValue = "false")
//public class SierraIngestSource implements MarcIngestSource<BibResult> {
//
//	public static final String CONFIG_ROOT = "sierra.ingest";
//
//	private static Logger log = LoggerFactory.getLogger(SierraIngestSource.class);
//
//	private final SierraApiClient sierraApi;
//	private final RawSourceRepository rawSourceRepository;
//
//	SierraIngestSource(SierraApiClient sierraApi, RawSourceRepository rawSourceRepository) {
//		this.sierraApi = sierraApi;
//		this.rawSourceRepository = rawSourceRepository;
//	}
//	
//	private Mono<BibResultSet> fetchPage (Instant since, int offset, int limit) {
//		log.info("Fetching batch from Sierra API with since={} offset={} limit={}", since, offset, limit);
//		return Mono.from(sierraApi.bibs(params -> {
//			params
//				.deleted(false)
//				.offset(offset)
//				.limit(limit)
//				.fields(fieldList);
//
//			if (since != null) {
//				params
//					.updatedDate(dtr -> {
//						dtr
//							.to(LocalDateTime.now())
//							.fromDate(LocalDateTime.from(since));
//					});
//			}
//		}));
//	}
//	
//	private static final List<String> fieldList = List.of(
//			"id", "updatedDate", "createdDate",
//			"deletedDate", "deleted", "marc");
//	
//	private Publisher<BibResult> pageAllResults(Instant since, int offset, int limit) {
//		
//		return fetchPage(since, offset, limit)
//			.expand(results -> {
//				var bibs = results.entries();
//				
//				log.info("Fetched a chunk of {} records", bibs.size());
//				final int nextOffset = results.start() + bibs.size();
//				final boolean possiblyMore = bibs.size() == limit;
//				
//				if (!possiblyMore) {
//					log.info("No more results to fetch");
//					return Mono.empty();
//				}
//				
//				return fetchPage(since, nextOffset, limit);
//			})
//			
//			.concatMap(results -> Flux.fromIterable( new ArrayList<>( results.entries() )));
//	}
//
//	@Override
//	@NotNull
//	public String getDefaultControlIdNamespace() {
//		return "sierra";
//	}
//
//	@Override
//	public Publisher<BibResult> getResources(Instant since) {
//		log.info("Fetching MARC JSON from Sierra");
//
//		// The stream of imported records.
//		return Flux.from(pageAllResults(since, 0, 2000))
//			.filter(sierraBib -> Objects.nonNull(sierraBib.marc()))
//			.switchIfEmpty(completeAsEmpty());
//	}
//	
//	private <T> Mono<T> completeAsEmpty() {
//		
//		log.info("No results returned. Stopping");
//		return Mono.empty();
//	}
//
//	@Override
//	public IngestRecordBuilder initIngestRecordBuilder(BibResult resource) {
//		return IngestRecord.builder()
//			.uuid(uuid5ForBibResult(resource));
//	}
//	
//	private static final String UUID5_PREFIX = "ingest-source:sierra";
//	
//	public UUID uuid5ForBibResult( @NotNull final BibResult result ) {
//		
//		final String concat = UUID5_PREFIX + ":" + result.id();
//		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
//	}
//
//	@Override
//	public Record resourceToMarc(BibResult resource) {
//		return resource.marc();
//	}
//
//	@Override
//	public String getName() {
//		return this.getClass().getName();
//	}
//
//	@Override
//	public RawSourceRepository getRawSourceRepository() {
//		return rawSourceRepository;
//	}
//
//	@Override
//	public RawSource resourceToRawSource(BibResult resource) {
//		
//		
//		// TODO Auto-generated method stub
//		return null;
//	}
//}

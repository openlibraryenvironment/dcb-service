package org.olf.reshare.dcb.core.interaction.sierra;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.net.MalformedURLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import org.marc4j.marc.Record;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.ingest.marc.MarcIngestSource;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord.Builder;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.bibs.BibResult;
import services.k_int.utils.UUIDUtils;

@Prototype
public class SierraLmsClient implements HostLmsClient, MarcIngestSource<BibResult> {
	
	private static final Logger log = LoggerFactory.getLogger(SierraLmsClient.class);
	
	private final HostLms lms;
	private final SierraApiClient client;
	
	public SierraLmsClient( @Parameter HostLms lms, HostLmsSierraApiClientFactory clientFactory ) throws MalformedURLException {
		this.lms = lms;
		
		// Get a sierra api client.
		client = clientFactory.createClientFor(lms);
	}
	
	@Override
	public HostLms getHostLms() {
		return lms;
	}
	
	private Flux<BibResult> scrollAllBibs(final Instant since, final int offset, final int limit) {
		log.info("Fetching batch from Sierra API");
		 
		final Instant fromTime = since == null ? null : since.truncatedTo(ChronoUnit.SECONDS);
		return Mono.from(client.bibs(params -> {
			params
				.deleted(false)
				.offset(offset)
				.limit(limit)
				.addFields(
						"id", "updatedDate", "createdDate",
						"deletedDate", "deleted", "marc");
			
			if (fromTime != null) {
				params
					.updatedDate(dtr -> {
						dtr
							.to(LocalDateTime.now())
							.fromDate(LocalDateTime.from(fromTime));
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
				return possiblyMore ? Flux.concat(currentPage, scrollAllBibs(fromTime, nextOffset, limit)) : currentPage;
			});
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
		return Flux.from(scrollAllBibs(since, 0, 2000))
			.filter( sierraBib -> sierraBib.marc() != null )
			.switchIfEmpty(
					Mono.just("No results returned. Stopping")
						.mapNotNull(s -> {
							log.info(s);
							return null;
						}));
	}

	private static final String UUID5_PREFIX = "ingest-source:sierra-lms";
	
	public UUID uuid5ForBibResult( @NotNull final BibResult result ) {
		
		final String concat = UUID5_PREFIX + ":" + lms.getName() + ":" + result.id();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}
	
	@Override
	public Builder initIngestRecordBuilder(BibResult resource) {
		
		// Use the host LMS as the
		return IngestRecord.builder()
			.uuid(uuid5ForBibResult(resource));
	}

	@Override
	public Record resourceToMarc(BibResult resource) {
		return resource.marc();
	}

	@Override
	public Flux<Map<String, ?>> getAllBibData() {
		return Flux.from( client.bibs( params -> params.deleted(false) ))
			.flatMap(results -> Flux.fromIterable(results.entries()) )
			.map(bibRes -> {
				Map<String, Object> map = new HashMap<>();
				map.put("id", bibRes.id());
				return map;
			});
	}

	@Override
	public String getName() {
		return lms.getName();
	}
	
	@Override
	public boolean isEnabled() {
		final String ingestStr = (String)lms.getClientConfig().get("ingest");
		return ingestStr == null || StringUtils.isTrue(ingestStr);
	}
}

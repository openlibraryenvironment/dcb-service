package org.olf.reshare.dcb.core.interaction.sierra;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.net.MalformedURLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.marc4j.marc.Record;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.Item;
import org.olf.reshare.dcb.core.interaction.Location;
import org.olf.reshare.dcb.core.interaction.Status;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.ingest.marc.MarcIngestSource;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
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
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

@Prototype
public class SierraLmsClient implements HostLmsClient, MarcIngestSource<BibResult> {

	private static final Logger log = LoggerFactory.getLogger(SierraLmsClient.class);
	
	private static final int MAX_BUFFERED_ITEMS = 2000;

	private final HostLms lms;
	private final SierraApiClient client;

	public SierraLmsClient(@Parameter HostLms lms, HostLmsSierraApiClientFactory clientFactory)
			throws MalformedURLException {
		this.lms = lms;

		// Get a sierra api client.
		client = clientFactory.createClientFor(lms);
	}

	@Override
	public HostLms getHostLms() {
		return lms;
	}

	private Mono<BibResultSet> fetchPage(Instant since, int offset, int limit) {
		log.info("Fetching batch from Sierra API with since={} offset={} limit={}", since, offset, limit);
		return Mono.from(client.bibs(params -> {
			params.deleted(false).offset(offset).limit(limit)
					.fields(List.of("id", "updatedDate", "createdDate", "deletedDate", "deleted", "marc"));

			if (since != null) {
				params.updatedDate(dtr -> {
					dtr.to(LocalDateTime.now()).fromDate(LocalDateTime.from(since));
				});
			}
		}));
	}

	private Publisher<BibResult> pageAllResults(Instant since, int offset, int limit) {
		
		return fetchPage(since, offset, limit).expand(results -> {
			var bibs = results.entries();

			log.info("Fetched a chunk of {} records", bibs.size());
			final int nextOffset = results.start() + bibs.size();
			final boolean possiblyMore = bibs.size() == limit;

			if (!possiblyMore) {
				log.info("No more results to fetch");
				return Mono.empty();
			}

			return fetchPage(since, nextOffset, limit);
		}, limit)
		.concatMap(results -> Flux.fromIterable(new ArrayList<>(results.entries())), (MAX_BUFFERED_ITEMS / limit) + 1)
		.onBackpressureBuffer(MAX_BUFFERED_ITEMS);
	}

	@Override
	@NotNull
	public String getDefaultControlIdNamespace() {
		return "sierra";
	}

	@Override
	public Publisher<BibResult> getResources(Instant since) {
		log.info("Fetching MARC JSON from Sierra");

		final int pageSize = MapUtils.getAsOptionalString(lms.getClientConfig(), "page-size").map(Integer::parseInt)
				.orElse(DEFAULT_PAGE_SIZE);

		// The stream of imported records.
		return Flux.from(pageAllResults(since, 0, pageSize)).filter(sierraBib -> sierraBib.marc() != null)
				.switchIfEmpty(Mono.just("No results returned. Stopping")
						.mapNotNull(s -> {
							log.info(s);
							return null;
						}));
	}

	private static final String UUID5_PREFIX = "ingest-source:sierra-lms";

	public UUID uuid5ForBibResult(@NotNull final BibResult result) {

		final String concat = UUID5_PREFIX + ":" + lms.getName() + ":" + result.id();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public IngestRecordBuilder initIngestRecordBuilder(BibResult resource) {

		// Use the host LMS as the
		return IngestRecord.builder()
				.uuid(uuid5ForBibResult(resource))
				.sourceSystemId(lms.getId())
				.sourceRecordId(resource.id());
	}

	@Override
	public Record resourceToMarc(BibResult resource) {
		return resource.marc();
	}

	@Override
	public Flux<Map<String, ?>> getAllBibData() {
		return Flux.from(client.bibs(params -> params.deleted(false)))
				.flatMap(results -> Flux.fromIterable(results.entries())).map(bibRes -> {
					Map<String, Object> map = new HashMap<>();
					map.put("id", bibRes.id());
					return map;
				});
	}

	@Override
	public Flux<Item> getAllItemDataByBibRecordId(String bibRecordId) {
		log.info("getAllItemDataByBibRecordId({})", bibRecordId);
		return Flux.from(client.items(params -> params
				.deleted(false)
				.bibIds(List.of(bibRecordId))))
			.flatMap(results -> Flux.fromIterable(results.getEntries()))
			.map(itemRes -> new Item(itemRes.getId(),
				new Status(itemRes.getStatus().getCode(),
					itemRes.getStatus().getDisplay(),
					itemRes.getStatus().getDuedate()),
				new Location(itemRes.getLocation().getCode(),
					itemRes.getLocation().getName())));
	}

	@Override
	public String getName() {
		return lms.getName();
	}

	@Override
	public boolean isEnabled() {
		return MapUtils.getAsOptionalString(lms.getClientConfig(), "ingest").map(StringUtils::isTrue).orElse(Boolean.TRUE);
	}
}

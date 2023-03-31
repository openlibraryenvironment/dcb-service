package org.olf.reshare.dcb.core.interaction.sierra;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.olf.reshare.dcb.ingest.model.RawSource;
import org.olf.reshare.dcb.storage.RawSourceRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.tree.JsonNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.bibs.BibResult;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.interaction.sierra.items.Result;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

@Prototype
public class SierraLmsClient implements HostLmsClient, MarcIngestSource<BibResult> {
	private static final Logger log = LoggerFactory.getLogger(SierraLmsClient.class);
	
	private static final int MAX_BUFFERED_ITEMS = 2000;
	
	private final ConversionService<?> conversionService = ConversionService.SHARED;

	private final HostLms lms;
	private final SierraApiClient client;
	private final SierraResponseErrorMatcher sierraResponseErrorMatcher = new SierraResponseErrorMatcher();

	private final RawSourceRepository rawSourceRepository;

	public SierraLmsClient(@Parameter HostLms lms, HostLmsSierraApiClientFactory clientFactory, RawSourceRepository rawSourceRepository)  {
		this.lms = lms;

		// Get a sierra api client.
		client = clientFactory.createClientFor(lms);
		this.rawSourceRepository = rawSourceRepository;
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
		return lms.getName();
	}

	@Override
	public Publisher<BibResult> getResources(Instant since) {
		log.info("Fetching MARC JSON from Sierra for {}", lms.getName());

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

		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + result.id();
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

	public UUID uuid5ForRawJson(@NotNull final BibResult result) {

		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":raw:" + result.id();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public RawSource resourceToRawSource(BibResult resource) {
		
		final JsonNode rawJson = conversionService.convertRequired(resource.marc(), JsonNode.class);
		
		@SuppressWarnings("unchecked")
		final Map<String,?> rawJsonString = conversionService.convertRequired(rawJson, Map.class);
		
		
		RawSource raw = RawSource.builder()
				.id(uuid5ForRawJson(resource))
				.hostLmsId(lms.getId())
				.remoteId(resource.id())
				.json(rawJsonString)
				.build();
		
		return raw;
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
	public Mono<List<Item>> getItemsByBibId(String bibId, String hostLmsCode) {
		log.info("getItemsByBibId({})", bibId);

		return Flux.from(client.items(params -> params
				.deleted(false)
				.bibIds(List.of(bibId))))
			.flatMap(results -> Flux.fromIterable(results.getEntries()))
			.map(result -> mapResultToItem(result, hostLmsCode))
			.collectList()
			.onErrorReturn(sierraResponseErrorMatcher::isNoRecordsError, List.of());
	}

	private static Item mapResultToItem(Result result, String hostLmsCode) {
		return new Item(result.getId(),
			new Status(result.getStatus().getCode(),
				result.getStatus().getDisplay(),
				result.getStatus().getDuedate()),
			new Location(result.getLocation().getCode(),
				result.getLocation().getName()),
			result.getBarcode(), result.getCallNumber(),
			hostLmsCode);
	}

	@Override
	public String getName() {
		return lms.getName();
	}

	@Override
	public boolean isEnabled() {
		return MapUtils.getAsOptionalString(lms.getClientConfig(), "ingest").map(StringUtils::isTrue).orElse(Boolean.TRUE);
	}

	@Override
	public RawSourceRepository getRawSourceRepository() {
		return rawSourceRepository;
	}
}

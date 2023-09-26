package org.olf.dcb.core.interaction.polaris.papi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.marc4j.marc.Record;
import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.ingest.marc.MarcIngestSource;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.ingest.model.RawSource;
import org.olf.dcb.storage.RawSourceRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micronaut.context.annotation.Parameter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import services.k_int.utils.UUIDUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static io.micronaut.http.HttpMethod.GET;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.Integer.parseInt;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.core.interaction.polaris.papi.MarcConverter.convertToMarcRecord;
import static org.olf.dcb.core.interaction.polaris.papi.PAPIConstants.*;
import static reactor.function.TupleUtils.function;

@Prototype
public class PAPILmsClient implements MarcIngestSource<PAPILmsClient.BibsPagedRow>, HostLmsClient{
	private static final Logger log = LoggerFactory.getLogger(PAPILmsClient.class);
	private final HostLms lms;
	private final URI rootUri;
	private final HttpClient client;
	private final ProcessStateService processStateService;
	private final RawSourceRepository rawSourceRepository;
	private final ConversionService conversionService;
	private final AuthFilter authFilter;

	@Creator
	public PAPILmsClient(
		@Parameter("hostLms") HostLms hostLms,
		@Parameter("client") HttpClient client,
		ProcessStateService processStateService,
		RawSourceRepository rawSourceRepository,
		ConversionService conversionService)
	{
		log.debug("Creating PAPI HostLms client for HostLms {}", hostLms);
		rootUri = UriBuilder.of((String) hostLms.getClientConfig().get(CLIENT_BASE_URL)).build();

		lms = hostLms;
		this.authFilter = new AuthFilter(this);
		this.processStateService = processStateService;
		this.rawSourceRepository = rawSourceRepository;
		this.conversionService = conversionService;
		this.client = client;
	}

	private Mono<PublisherState> getInitialState(UUID context, String process) {
		return processStateService.getStateMap(context, process)
			.defaultIfEmpty(new HashMap<>())
			.map(currentStateMap -> {
				PublisherState generatorState = new PublisherState(currentStateMap);
				log.info("backpressureAwareBibResultGenerator - state={} lmsid={} thread={}",
					currentStateMap, lms.getId(), Thread.currentThread().getName());

				String cursor = (String) currentStateMap.get("cursor");
				if (cursor != null) {
					log.debug("Cursor: " + cursor);
					String[] components = cursor.split(":");

					if (components.length > 1) {
						switch (components[0]) {
							case "bootstrap":
								generatorState.offset = parseInt(components[1]);
								log.info("Resuming bootstrap for {} at offset {}", lms.getName(), generatorState.offset);
								break;
							case "deltaSince":
								generatorState.sinceMillis = Long.parseLong(components[1]);
								generatorState.since = Instant.ofEpochMilli(generatorState.sinceMillis);
								if (components.length == 3) {
									generatorState.offset = parseInt(components[2]);
								}
								log.info("Resuming delta at timestamp {} offset={} name={}", generatorState.since, generatorState.offset, lms.getName());
								break;
						}
					}
				} else {
					log.info("Start a fresh ingest");
				}

				// Make a note of the time before we start
				generatorState.request_start_time = System.currentTimeMillis();
				log.debug("Create generator: name={} offset={} since={}",
					lms.getName(), generatorState.offset, generatorState.since);

				return generatorState;
			});
	}

	@Transactional(value = Transactional.TxType.REQUIRES_NEW)
	protected Mono<PublisherState> saveState(PublisherState state) {
		log.debug("Update state {} - {}", state,lms.getName());

		return Mono.from(processStateService.updateState(lms.getId(), "ingest", state.storred_state))
			.thenReturn(state);
	}

	private Mono<BibsPagedResult> fetchPage(Instant updatedate, Integer lastId, Integer nrecs) {
		log.info("Creating subscribeable batch from last id;  {}, {}", lastId, nrecs);
		final var date = formatDateFrom(updatedate);
		return Mono.from( synch_BibsPagedGet(date, lastId, nrecs) )
			//.doOnSuccess(bibsPagedResult -> log.debug("result of bibPagedResult: {}", bibsPagedResult))
			.doOnSubscribe(_s -> log.info("Fetching batch from Sierra {} with since={} offset={} limit={}",
				lms.getName(), updatedate, lastId, nrecs));	}

	@SingleResult
	public Publisher<BibsPagedResult> synch_BibsPagedGet(String updatedate, Integer lastId, Integer nrecs) {
		String path = "/PAPIService/REST/protected" + getGeneralUriParameters() + "/synch/bibs/MARCXML/paged";
		return getRequest(path, Argument.of(BibsPagedResult.class),
			uri -> uri
				.queryParam("updatedate", updatedate)
				.queryParam("lastid", lastId)
				.queryParam("nrecs", nrecs));
	}

	public String getGeneralUriParameters() {
		final Map<String, Object> conf = lms.getClientConfig();
//		log.debug(conf.toString());
		final String version = (String) conf.get(VERSION);
		final String langId = (String) conf.get(LANG_ID);
		final String appId = (String) conf.get(APP_ID);
		final String orgId = (String) conf.get(ORG_ID);

		if (version == null || langId == null || appId == null || orgId == null) {
			log.error("One or more parameter values are null: version={}, langId={}, appId={}, orgId={}. Returning null.",
				version, langId, appId, orgId);
			return null;
		}

		return String.format("/%s/%s/%s/%s", version, langId, appId, orgId);
	}

	private static URI resolve(URI baseUri, URI relativeURI) {
		URI thisUri = baseUri;

		// if the URI features credentials strip this out
		if (StringUtils.isNotEmpty(thisUri.getUserInfo())) {
			try {
				thisUri = new URI(thisUri.getScheme(), null, thisUri.getHost(), thisUri.getPort(), thisUri.getPath(),
					thisUri.getQuery(), thisUri.getFragment());
			} catch (URISyntaxException e) {
				throw new IllegalStateException("URI is invalid: " + e.getMessage(), e);
			}
		}

		final var rawQuery = thisUri.getRawQuery();

		if (StringUtils.isNotEmpty(rawQuery)) {
			return thisUri.resolve(relativeURI + "?" + rawQuery);
		} else {
			return thisUri.resolve(relativeURI);
		}
	}

	public Mono<AuthFilter.AuthToken> acquireAccessToken() {
		final Map<String, Object> conf = lms.getClientConfig();
		final String domain = (String) conf.get(DOMAIN_ID);
		final String username = (String) conf.get(STAFF_USERNAME);
		final String password = (String) conf.get(STAFF_PASSWORD);

		return Mono.from( staffAuthenticator(domain, username, password) );
	}

	private Mono<AuthFilter.AuthToken> staffAuthenticator(String domain, String username, String password) {
		return Mono.just(UriBuilder.of("/PAPIService/REST/protected" + getGeneralUriParameters() + "/authenticator/staff").build())
			.map(this::resolve)
			.map(resolvedUri -> HttpRequest.create(POST, resolvedUri.toString()).accept(APPLICATION_JSON))
			.map(request -> request.body(StaffCredentials.builder().Domain(domain).Username(username).Password(password).build()))
			.map(authFilter::authorization)
			.flatMap(request -> Mono.from(client.exchange(request, AuthFilter.AuthToken.class)))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()));
	}

	private <T> Mono<MutableHttpRequest<?>> postRequest(String path) {
		return createRequest(POST, path).flatMap( authFilter::ensureAuth );
	}

	private <T> Mono<T> getRequest(String path, Argument<T> argumentType,
		Consumer<UriBuilder> uriBuilderConsumer) {

		return createRequest(GET, path).map(req -> req.uri(uriBuilderConsumer))
			.flatMap( authFilter::ensureAuth )
			.flatMap(request -> Mono.from(client.retrieve(request, argumentType)));
	}

	public <T> Mono<MutableHttpRequest<?>> createRequest(HttpMethod method, String path) {
		return Mono.just(UriBuilder.of(path).build())
			.map(this::resolve)
			.map(resolvedUri -> HttpRequest.<T>create(method, resolvedUri.toString()).accept(APPLICATION_JSON));
	}

	private URI resolve(URI relativeURI) {
		return resolve(rootUri, relativeURI);
	}

	@Override
	public Publisher<ConfigurationRecord> getConfigStream() {
		log.debug("{}, {}", "getConfigStream() not implemented, returning: ", null);
		return Mono.empty();
	}

	@Override
	public String getDefaultControlIdNamespace() {
		return lms.getName();
	}

	@Override
	public Publisher<BibsPagedRow> getResources(Instant since) {
		log.info("Fetching MARC JSON from Polaris for {}", lms.getName());

		return Flux.from( pageAllResults(MAX_BIBS) )
			.filter(bibsPagedRow -> {
				log.debug("getResources({}), ", bibsPagedRow);
				return bibsPagedRow.getBibliographicRecordXML() != null;
			})

			.onErrorResume(t -> {
				log.error("Error ingesting data {}", t.getMessage());
				t.printStackTrace();
				return Mono.empty();
			})
			.switchIfEmpty(
				Mono.fromCallable(() -> {
					log.info("No results returned. Stopping");
					return null;
				}));
	}

	private Publisher<BibsPagedRow> pageAllResults(int pageSize) {
		return getInitialState(lms.getId(), "ingest")
			.flatMapMany(state -> fetchPageAndUpdateState(state, pageSize))
			.concatMap(function(this::processPageAndSaveState));
	}

	private Flux<Tuple2<PublisherState, BibsPagedResult>> fetchPageAndUpdateState(PublisherState state, int pageSize) {
		return Mono.zip(Mono.just(state.toBuilder().build()), fetchPage(state.since, state.offset, pageSize))
			.expand(function((currentState, results) -> {
				var bibs = results.getGetBibsPagedRows();
				log.info("Fetched a chunk of {} records for {}", bibs.size(), lms.getName());
				log.info("got page {} of data, containing {} results", currentState.page_counter++, bibs.size());
				currentState.possiblyMore = bibs.size() == pageSize;

				if (!currentState.possiblyMore) {
					log.info("{} ingest Terminating cleanly - run out of bib results - new timestamp is {}", lms.getName(), currentState.request_start_time);
					currentState.storred_state.put("cursor", "deltaSince:" + currentState.request_start_time);
					currentState.storred_state.put("name", lms.getName());
					log.info("No more results to fetch from {}", lms.getName());
					return Mono.empty();
				} else {
					log.info("Exhausted current page from {}, prep next", lms.getName());
					if (currentState.since != null) {
						currentState.storred_state.put("cursor", "deltaSince:" + currentState.sinceMillis + ":" + currentState.offset);
					} else {
						currentState.storred_state.put("cursor", "bootstrap:" + currentState.offset);
					}
				}
				return Mono.just(currentState.toBuilder().build())
					.zipWhen(updatedState -> fetchPage(updatedState.since, updatedState.offset, pageSize));
			}));
	}

	private Publisher<BibsPagedRow> processPageAndSaveState(PublisherState state, BibsPagedResult page) {
		state.offset = page.getLastID();
		log.debug("page getting converted to iterable: {}", page);

		return Flux.fromIterable(page.getGetBibsPagedRows())
			.concatWith(Mono.defer(() -> saveState(state))
				.flatMap(_s -> {
					log.debug("Updating state...");
					return Mono.empty();
				}))
			.doOnComplete(() -> log.debug("Consumed {} items", page.getGetBibsPagedRows().size()));
	}

	@Override
	public IngestRecord.IngestRecordBuilder initIngestRecordBuilder(BibsPagedRow resource) {
		return IngestRecord.builder()
			.uuid(uuid5ForBibPagedRow(resource))
			.sourceSystem(lms)
			.sourceRecordId(String.valueOf(resource.getBibliographicRecordID()))
			// TODO: resolve differences from sierra
			.suppressFromDiscovery(!resource.getIsDisplayInPAC())
			.deleted(false);
	}

	@Override
	public Record resourceToMarc(BibsPagedRow resource) {
		return convertToMarcRecord( resource.getBibliographicRecordXML() );
	}

	@Override
	public RawSourceRepository getRawSourceRepository() {
		return rawSourceRepository;
	}

	@Override
	public RawSource resourceToRawSource(BibsPagedRow resource) {
//		log.debug("resourceToRawSource: {}", resource);

		Record record = convertToMarcRecord( resource.getBibliographicRecordXML() );
		final JsonNode rawJson = conversionService.convertRequired(record, JsonNode.class);

		@SuppressWarnings("unchecked")
		final Map<String, ?> rawJsonString = conversionService.convertRequired(rawJson, Map.class);

		RawSource raw = RawSource.builder().id(uuid5ForRawJson(record)).hostLmsId(lms.getId()).remoteId(String.valueOf(record.getId()))
			.json(rawJsonString).build();

		return raw;
	}

	public UUID uuid5ForBibPagedRow(@NotNull final BibsPagedRow result) {

		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + result.getBibliographicRecordID();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	public UUID uuid5ForRawJson(@NotNull final Record result) {

		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":raw:" + result.getId();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	public static String formatDateFrom(Instant instant) {

		if (instant == null) return null;

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));
		return formatter.format(instant);
	}

	@Override
	public @NonNull String getName() {
		return null;
	}

	@Override
	public HostLms getHostLms() { return lms; }

	@Override
	public Flux<Map<String, ?>> getAllBibData() {
		return null;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return null;
	}

	@Override
	public Mono<List<Item>> getItemsByBibId(String bibId, String hostLmsCode) {
		return null;
	}

	@Override
	public Mono<String> createPatron(Patron patron) {
		return null;
	}

	@Override
	public Mono<String> createBib(Bib bib) {
		return null;
	}

	@Override
	public Mono<Tuple2<String, String>> placeHoldRequest(String id, String recordType, String recordNumber, String pickupLocation, String note, String patronRequestId) {
		return null;
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		return null;
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		return null;
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret) {
		return null;
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand createItemCommand) {
		return null;
	}

	@Override
	public Mono<HostLmsHold> getHold(String holdId) {
		return null;
	}

	@Override
	public Mono<HostLmsItem> getItem(String itemId) {
		return null;
	}

	@Override
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs) {
		return null;
	}

	@Override
	public Mono<String> checkOutItemToPatron(String itemId, String patronBarcode) {
		return null;
	}

	@Override
	public Mono<String> deleteItem(String id) {
		return null;
	}

	@Override
	public Mono<String> deleteBib(String id) {
		return null;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class StaffCredentials {
		private String Domain;
		private String Username;
		private String Password;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class BibsPagedResult {
		@JsonProperty("PAPIErrorCode")
		private Integer PAPIErrorCode;

		@JsonProperty("ErrorMessage")
		private String ErrorMessage;

		@JsonProperty("LastID")
		private Integer LastID;

		@JsonProperty("GetBibsPagedRows")
		private List<BibsPagedRow> GetBibsPagedRows;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class BibsPagedRow {
		@JsonProperty("BibliographicRecordID")
		private Integer BibliographicRecordID;

		@JsonProperty("IsDisplayInPAC")
		private Boolean IsDisplayInPAC;

		@JsonProperty("CreationDate")
		private String CreationDate;

		@JsonProperty("FirstAvailableDate")
		private String FirstAvailableDate;

		@JsonProperty("ModificationDate")
		@JsonInclude(JsonInclude.Include.NON_NULL)
		private String ModificationDate;

		@JsonProperty("BibliographicRecordXML")
		@JsonInclude(JsonInclude.Include.NON_NULL)
		private String BibliographicRecordXML;
	}

	@Builder(toBuilder = true)
	@ToString
	@RequiredArgsConstructor
	@AllArgsConstructor
	protected static class PublisherState {

		public final Map<String, Object> storred_state;

		@Builder.Default
		boolean possiblyMore = false;

		@Builder.Default
		int offset = 0;

		@Builder.Default
		Instant since = null;

		@Builder.Default
		long sinceMillis = 0;

		@Builder.Default
		long request_start_time = 0;

		@Builder.Default
		boolean error = false;
		@Builder.Default
		int page_counter = 0;

	}
}

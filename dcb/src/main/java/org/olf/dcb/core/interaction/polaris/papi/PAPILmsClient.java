package org.olf.dcb.core.interaction.polaris.papi;

import static io.micronaut.http.HttpMethod.GET;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.String.valueOf;
import static java.util.Collections.singletonList;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.core.interaction.polaris.papi.MarcConverter.convertToMarcRecord;
import static org.olf.dcb.core.interaction.polaris.papi.PAPIConstants.APP_ID;
import static org.olf.dcb.core.interaction.polaris.papi.PAPIConstants.CLIENT_BASE_URL;
import static org.olf.dcb.core.interaction.polaris.papi.PAPIConstants.LANG_ID;
import static org.olf.dcb.core.interaction.polaris.papi.PAPIConstants.MAX_BIBS;
import static org.olf.dcb.core.interaction.polaris.papi.PAPIConstants.ORG_ID;
import static org.olf.dcb.core.interaction.polaris.papi.PAPIConstants.UUID5_PREFIX;
import static org.olf.dcb.core.interaction.polaris.papi.PAPIConstants.VERSION;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.marc4j.marc.Record;
import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsHold;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.RelativeUriResolver;
import org.olf.dcb.core.interaction.shared.ItemResultToItemMapper;
import org.olf.dcb.core.interaction.shared.PublisherState;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.ingest.marc.MarcIngestSource;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.ingest.model.RawSource;
import org.olf.dcb.storage.RawSourceRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import services.k_int.utils.UUIDUtils;
import services.k_int.utils.MapUtils;
import io.micronaut.core.util.StringUtils;


@Prototype
public class PAPILmsClient implements MarcIngestSource<PAPILmsClient.BibsPagedRow>, HostLmsClient{
	private static final Logger log = LoggerFactory.getLogger(PAPILmsClient.class);
	private final URI rootUri;
	private final HostLms lms;
	private final HttpClient client;
	private final ProcessStateService processStateService;
	private final RawSourceRepository rawSourceRepository;
	private final ConversionService conversionService;
	private final AuthFilter authFilter;
	private final IngestHelper ingestHelper;
	private final ItemResultToItemMapper itemResultToItemMapper;

	@Creator
	public PAPILmsClient(
		@Parameter("hostLms") HostLms hostLms,
		@Parameter("client") HttpClient client,
		ProcessStateService processStateService,
		RawSourceRepository rawSourceRepository,
		ConversionService conversionService, ItemResultToItemMapper itemResultToItemMapper) {
		log.debug("Creating PAPI HostLms client for HostLms {}", hostLms);
		rootUri = UriBuilder.of((String) hostLms.getClientConfig().get(CLIENT_BASE_URL)).build();

		lms = hostLms;
		this.itemResultToItemMapper = itemResultToItemMapper;
		this.ingestHelper = new IngestHelper(this, hostLms, processStateService);
		this.authFilter = new AuthFilter(this);
		this.processStateService = processStateService;
		this.rawSourceRepository = rawSourceRepository;
		this.conversionService = conversionService;
		this.client = client;
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret) {
		//log.debug("patronAuth({})", authProfile);
		return switch (authProfile) {
			case "BASIC/BARCODE+PASSWORD" -> patronValidate(patronPrinciple, secret);
			default -> Mono.empty();
		};
	}

	@SingleResult
	public Mono<Patron> patronValidate(String barcode, String password) {
		final var path = "/PAPIService/REST/public" + getGeneralUriParameters() + "/patron/" + barcode;
		final var patronCredentials = PatronCredentials.builder().barcode(barcode).password(password).build();
		return getRequest(path, Argument.of(PatronValidateResult.class), uri -> {}, patronCredentials)
			.filter(PatronValidateResult::getValidPatron)
			.map(patronValidateResult -> Patron.builder()
				.localId(singletonList(valueOf(patronValidateResult.getPatronID())))
				.localPatronType(valueOf(patronValidateResult.getPatronCodeID()))
				.localBarcodes(singletonList(patronValidateResult.getBarcode()))
				.localHomeLibraryCode(valueOf(patronValidateResult.getAssignedBranchID()))
				.build());
	}

	@SingleResult
	public Publisher<BibsPagedResult> synch_BibsPagedGet(String updatedate, Integer lastId, Integer nrecs) {
		String path = "/PAPIService/REST/protected" + getGeneralUriParameters() + "/synch/bibs/MARCXML/paged";
		return getRequest(path, Argument.of(BibsPagedResult.class),
			uri -> uri
				.queryParam("updatedate", updatedate)
				.queryParam("lastid", lastId)
				.queryParam("nrecs", nrecs));
	}

	@Override
	public Mono<List<Item>> getItems(String localBibId) {
		String path = "/PAPIService/REST/protected" + getGeneralUriParameters() + "/synch/items/bibid/" + localBibId;
		return getRequest(path, Argument.of(ItemGetResponse.class),
			uri -> uri.queryParam("excludeecontent", false))
			.map(ItemGetResponse::getItemGetRows)
			.flatMapMany(Flux::fromIterable)
			.flatMap(result -> itemResultToItemMapper.mapItemGetRowToItem(result, lms.getCode(), localBibId))
			.collectList();
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

	public <T> Mono<HttpResponse<T>> exchange(MutableHttpRequest<?> request, Class<T> returnClass) {
		return Mono.from(client.exchange(request, returnClass));
	}

	private <T> Mono<MutableHttpRequest<?>> postRequest(String path) {
		return createRequest(POST, path).flatMap( authFilter::ensureStaffAuth);
	}

	private <T> Mono<T> getRequest(String path, Argument<T> argumentType,
		Consumer<UriBuilder> uriBuilderConsumer) {

		return createRequest(GET, path).map(req -> req.uri(uriBuilderConsumer))
			.flatMap( authFilter::ensureStaffAuth)
			.flatMap(request -> Mono.from(client.retrieve(request, argumentType)));
	}

	private <T> Mono<T> getRequest(String path, Argument<T> argumentType,
		Consumer<UriBuilder> uriBuilderConsumer, PatronCredentials patronCredentials) {

		return createRequest(GET, path).map(req -> req.uri(uriBuilderConsumer))
			.flatMap(req -> authFilter.ensurePublicAuth(req, patronCredentials))
			.flatMap(request -> Mono.from(client.retrieve(request, argumentType)));
	}

	public <T> Mono<MutableHttpRequest<?>> createRequest(HttpMethod method, String path) {
		return Mono.just(UriBuilder.of(path).build())
			.map(this::resolve)
			.map(resolvedUri -> HttpRequest.<T>create(method, resolvedUri.toString()).accept(APPLICATION_JSON));
	}

	URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(rootUri, relativeURI);
	}

	public UUID uuid5ForBibPagedRow(@NotNull final BibsPagedRow result) {

		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + result.getBibliographicRecordID();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	public UUID uuid5ForRawJson(@NotNull final BibsPagedRow result) {

		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":raw:" + result.getBibliographicRecordID();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	public static String formatDateFrom(Instant instant) {

		if (instant == null) return null;

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));
		return formatter.format(instant);
	}

        @Override
        public boolean isEnabled() {
                return MapUtils.getAsOptionalString(lms.getClientConfig(), "ingest").map(StringUtils::isTrue).orElse(Boolean.TRUE);
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

	@Override
	public String getDefaultControlIdNamespace() {
		return lms.getName();
	}

	@Override
	public Publisher<BibsPagedRow> getResources(Instant since) {
		log.info("Fetching MARC JSON from Polaris for {}", lms.getName());

		final Map<String, Object> conf = lms.getClientConfig();
		Integer pageSize = (Integer) conf.get(MAX_BIBS);
                if ( pageSize > 100 ) {
                        log.info("Limiting POLARIS page size to 100");
                        pageSize = 100;
                }

		return Flux.from( ingestHelper.pageAllResults(pageSize) )
			.filter(bibsPagedRow -> {
//				log.debug("getResources({}), ", bibsPagedRow);
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

		RawSource raw = RawSource.builder().id(uuid5ForRawJson(resource)).hostLmsId(lms.getId()).remoteId(String.valueOf(record.getId()))
			.json(rawJsonString).build();

		return raw;
	}

	@Override
	public Publisher<ConfigurationRecord> getConfigStream() {
		log.debug("{}, {}", "getConfigStream() not implemented, returning: ", null);
		return Mono.empty();
	}

	@Override
	public ProcessStateService getProcessStateService() {
		return this.processStateService;
	}

	@Override
	public PublisherState mapToPublisherState(Map<String, Object> mapData) {
		return ingestHelper.mapToPublisherState(mapData);
	}

	@Override
	public Publisher<PublisherState> saveState(UUID context, String process, PublisherState state) {
		return ingestHelper.saveState(state);
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class PatronCredentials {
		@JsonProperty("Barcode")
		private String barcode;
		@JsonProperty("Password")
		private String password;
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
	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class ItemGetResponse {
		@JsonProperty("PAPIErrorCode")
		private Integer PAPIErrorCode;

		@JsonProperty("ErrorMessage")
		private String ErrorMessage;

		@JsonProperty("ItemGetRows")
		private List<ItemGetRow> ItemGetRows;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class ItemGetRow {
		@JsonProperty("LocationID")
		private Integer LocationID;

		@JsonProperty("LocationName")
		private String LocationName;

		@JsonProperty("CollectionID")
		private Integer CollectionID;

		@JsonProperty("CollectionName")
		private String CollectionName;

		@JsonProperty("Barcode")
		private String Barcode;

		@JsonProperty("PublicNote")
		private String PublicNote;

		@JsonProperty("CallNumber")
		private String CallNumber;

		@JsonProperty("Designation")
		private String Designation;

		@JsonProperty("VolumeNumber")
		private String VolumeNumber;

		@JsonProperty("ShelfLocation")
		private String ShelfLocation;

		@JsonProperty("CircStatus")
		private String CircStatus;

		@JsonProperty("LastCircDate")
		private String LastCircDate;

		@JsonProperty("MaterialType")
		private String MaterialType;

		@JsonProperty("TextualHoldingsNote")
		private String TextualHoldingsNote;

		@JsonProperty("RetentionStatement")
		private String RetentionStatement;

		@JsonProperty("HoldingsStatement")
		private String HoldingsStatement;

		@JsonProperty("HoldingsNote")
		private String HoldingsNote;

		@JsonProperty("Holdable")
		private Boolean Holdable;

		@JsonProperty("DueDate")
		private String DueDate;

		@JsonProperty("ItemRecordID")
		private Integer ItemRecordID;

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
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class PatronValidateResult {
		@JsonProperty("PAPIErrorCode")
		private Integer PAPIErrorCode;

		@JsonProperty("ErrorMessage")
		private String ErrorMessage;

		@JsonProperty("Barcode")
		private String Barcode;

		@JsonProperty("ValidPatron")
		private Boolean ValidPatron;

		@JsonProperty("PatronID")
		private Integer PatronID;

		@JsonProperty("PatronCodeID")
		private Integer PatronCodeID;

		@JsonProperty("AssignedBranchID")
		private Integer AssignedBranchID;

		@JsonProperty("PatronBarcode")
		private String PatronBarcode;

		@JsonProperty("AssignedBranchName")
		private String AssignedBranchName;

		@JsonProperty("ExpirationDate")
		private String ExpirationDate;

		@JsonProperty("OverridePasswordUsed")
		private Boolean OverridePasswordUsed;
	}
}

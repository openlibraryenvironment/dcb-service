package org.olf.dcb.core.interaction.sierra;

import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.util.Collections.singletonList;
import static java.util.Objects.nonNull;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.utils.DCBStringUtilities.deRestify;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.validation.constraints.NotNull;

import org.marc4j.marc.Record;
import org.olf.dcb.configuration.BranchRecord;
import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.configuration.PickupLocationRecord;
import org.olf.dcb.configuration.RefdataRecord;
import org.olf.dcb.configuration.LocationRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.ingest.marc.MarcIngestSource;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.olf.dcb.ingest.model.RawSource;
import org.olf.dcb.storage.RawSourceRepository;
import org.olf.dcb.tracking.model.LenderTrackingEvent;
import org.olf.dcb.tracking.model.PatronTrackingEvent;
import org.olf.dcb.tracking.model.PickupTrackingEvent;
import org.olf.dcb.tracking.model.TrackingRecord;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.tree.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.interaction.sierra.bibs.BibResult;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.interaction.sierra.configuration.BranchInfo;
import services.k_int.interaction.sierra.configuration.PickupLocationInfo;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.interaction.sierra.holds.SierraPatronHoldResultSet;
import services.k_int.interaction.sierra.items.ResultSet;
import services.k_int.interaction.sierra.items.SierraItem;
import services.k_int.interaction.sierra.patrons.ItemPatch;
import services.k_int.interaction.sierra.patrons.PatronHoldPost;
import services.k_int.interaction.sierra.patrons.PatronPatch;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

/**
 * See: https://sandbox.iii.com/iii/sierra-api/swagger/index.html
 * https://gitlab.com/knowledge-integration/libraries/dcb-service/-/raw/68fd93de0f84f928597481b16d2887bd7e58f455/dcb/src/main/java/org/olf/dcb/core/interaction/sierra/SierraLmsClient.java
 */
@Prototype
public class SierraLmsClient implements HostLmsClient, MarcIngestSource<BibResult> {
	private static final Logger log = LoggerFactory.getLogger(SierraLmsClient.class);

	private static final String UUID5_PREFIX = "ingest-source:sierra-lms";
	private static final Integer FIXED_FIELD_158 = Integer.valueOf(158);

	private final ConversionService<?> conversionService = ConversionService.SHARED;
	private final HostLms lms;
	private final SierraApiClient client;
	private final ProcessStateService processStateService;
	private final RawSourceRepository rawSourceRepository;
	private final ItemResultToItemMapper itemResultToItemMapper;

	public SierraLmsClient(@Parameter HostLms lms, HostLmsSierraApiClientFactory clientFactory,
		RawSourceRepository rawSourceRepository, ProcessStateService processStateService,
		ItemResultToItemMapper itemResultToItemMapper) {

		this.lms = lms;
		this.itemResultToItemMapper = itemResultToItemMapper;

		// Get a sierra api client.
		client = clientFactory.createClientFor(lms);
		this.rawSourceRepository = rawSourceRepository;
		this.processStateService = processStateService;
	}

	@Override
	public HostLms getHostLms() {
		return lms;
	}

	private Mono<BibResultSet> fetchPage(Instant since, int offset, int limit) {
		log.info("Creating subscribeable batch;  since={} offset={} limit={}", since, offset, limit);
		return Mono.from(client.bibs(params -> {
				params.deleted(false).offset(offset).limit(limit)
					.fields(List.of("id", "updatedDate", "createdDate", "deletedDate", "deleted", "marc", "suppressed"));

				if (since != null) {
					params.updatedDate(dtr -> {
						LocalDateTime from_as_local_date_time = since.atZone(java.time.ZoneId.of("UTC")).toLocalDateTime();
						log.info("Setting from date for {} to {}", lms.getName(), from_as_local_date_time);

						dtr
							.to(LocalDateTime.now())
							.fromDate(from_as_local_date_time);
					});
				}
			}))
			.doOnSubscribe(_s -> log.info("Fetching batch from Sierra {} with since={} offset={} limit={}",
				lms.getName(), since, offset, limit));
	}

	/**
	 * Use the ProcessStateRepository to get the current state for
	 * <idOfLms>:"ingest" process - a list of name value pairs If we don't find one,
	 * just create a new empty map transform that data into the PublisherState class
	 * above ^^
	 */
	private Mono<PublisherState> getInitialState(UUID context, String process) {
		return processStateService.getStateMap(context, process)
			.defaultIfEmpty(new HashMap<>())
			.map(current_state -> {
				PublisherState generator_state = new PublisherState(current_state);
				log.info("backpressureAwareBibResultGenerator - state=" + current_state + " lmsid=" + lms.getId() + " thread="
					+ Thread.currentThread().getName());

				String cursor = (String) current_state.get("cursor");
				if (cursor != null) {
					log.debug("Cursor: " + cursor);
					String[] components = cursor.split(":");

					if (components[0].equals("bootstrap")) {
						// Bootstrap cursor is used for the initial load where we need to just page
						// through everything
						// from day 0
						generator_state.offset = parseInt(components[1]);
						log.info("Resuming bootstrap for "+lms.getName()+" at offset " + generator_state.offset);
					} else if (components[0].equals("deltaSince")) {
						// Delta cursor is used after the initial bootstrap and lets us know the point
						// in time
						// from where we need to fetch records
						generator_state.sinceMillis = Long.parseLong(components[1]);
						generator_state.since = Instant.ofEpochMilli(generator_state.sinceMillis);
						if (components.length == 3) {
							// We're recovering from an interuption whilst processing a delta
							generator_state.offset = parseInt(components[2]);
						}
						log.info("Resuming delta at timestamp " + generator_state.since + " offset=" + generator_state.offset+" name="+lms.getName());
					}
				} else {
					log.info("Start a fresh ingest");
				}

				// Make a note of the time before we start
				generator_state.request_start_time = System.currentTimeMillis();
				log.debug("Create generator: name={} offset={} since={}", lms.getName(),
					generator_state.offset, generator_state.since);

				return generator_state;
			});
	}

	@Transactional(value = TxType.REQUIRES_NEW)
	protected Mono<PublisherState> saveState(PublisherState state) {
		log.debug("Update state {} - {}", state,lms.getName());

		return Mono.from(processStateService.updateState(lms.getId(), "ingest", state.storred_state))
			.thenReturn(state);
	}

	private Publisher<BibResult> pageAllResults(int pageSize) {

		return getInitialState(lms.getId(), "ingest")
			.flatMap(state -> Mono.zip(Mono.just(state.toBuilder().build()), fetchPage(state.since, state.offset, pageSize)))
			.expand(TupleUtils.function(( state ,results ) -> {

				var bibs = results.entries();
				log.info("Fetched a chunk of {} records for {}", bibs.size(),lms.getName());

//				state.current_page = bibs;

				log.info("got page {} of data, containing {} results", state.page_counter++, bibs.size());
				state.possiblyMore = bibs.size() == pageSize;

				// Increment the offset for the next fetch
				state.offset += bibs.size();

				// If we have exhausted the currently cached page, and we are at the end,
				// terminate.
				if (!state.possiblyMore) {
					log.info("{} ingest Terminating cleanly - run out of bib results - new timestamp is {}", lms.getName(), state.request_start_time);

					// Make a note of the time at which we started this run, so we know where
					// to pick up from next time
					state.storred_state.put("cursor", "deltaSince:" + state.request_start_time);
					state.storred_state.put("name", lms.getName());

					log.info("No more results to fetch from {}",lms.getName());
					return Mono.empty();

				} else {
					log.info("Exhausted current page from {} , prep next", lms.getName());
					// We have finished consuming a page of data, but there is more to come.
					// Remember where we got up to and stash it in the DB
					if (state.since != null) {
						state.storred_state.put("cursor",	"deltaSince:" + state.sinceMillis + ":" + state.offset);
					} else {
						state.storred_state.put("cursor", "bootstrap:" + state.offset);
					}
				}
				// Create a new mono that first saves the state and then uses it to fetch another page.
				return Mono.just(state.toBuilder().build()) // toBuilder().build() should copy the object.
					.zipWhen( updatedState -> fetchPage(updatedState.since, updatedState.offset, pageSize));
			}))
			.concatMap( TupleUtils.function((state, page) -> {
				return Flux.fromIterable(page.entries())
					// Concatenate with the state so we can propagate signals from the save operation.
					.concatWith(Mono.defer(() ->
							saveState(state))
						.flatMap(_s -> {
							log.debug("Updating state...");
							return Mono.empty();
						}))

					.doOnComplete(() -> log.debug("Consumed {} items", page.entries().size()));
			}));
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

		return Flux.from(pageAllResults(pageSize))
			.filter(sierraBib -> sierraBib.marc() != null)
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

	public UUID uuid5ForBibResult(@NotNull final BibResult result) {

		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + result.id();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public IngestRecordBuilder initIngestRecordBuilder(BibResult resource) {

		// Use the host LMS as the
		return IngestRecord.builder()
			.uuid(uuid5ForBibResult(resource))
			.sourceSystem(lms)
			.sourceRecordId(resource.id())
			.suppressFromDiscovery(resource.suppressed())
			.deleted(resource.deleted());
	}

	public UUID uuid5ForRawJson(@NotNull final BibResult result) {

		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":raw:" + result.id();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public RawSource resourceToRawSource(BibResult resource) {

		final JsonNode rawJson = conversionService.convertRequired(resource.marc(), JsonNode.class);

		@SuppressWarnings("unchecked")
		final Map<String, ?> rawJsonString = conversionService.convertRequired(rawJson, Map.class);

		RawSource raw = RawSource.builder().id(uuid5ForRawJson(resource)).hostLmsId(lms.getId()).remoteId(resource.id())
			.json(rawJsonString).build();

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
	public Mono<HostLmsItem> createItem(String bibId, String locationCode, String barcode) {

		return Mono.from(client.createItem(ItemPatch.builder()
				.bibIds(List.of(Integer.parseInt(bibId)))
				.location(locationCode)
				.barcodes(List.of(barcode))
				.build()))
			.doOnSuccess(result -> log.debug("the result of createItem({})", result))
			.map(result -> deRestify(result.getLink()))
			.map(localId -> HostLmsItem.builder()
				.localId(localId)
				.build());
	}

	@Override
	public Mono<List<Item>> getItemsByBibId(String bibId, String hostLmsCode) {
		log.debug("getItemsByBibId({})", bibId);

		return Mono.from(client.items(params -> params
				.deleted(false)
				.bibIds(List.of(bibId))
				.fields(List.of("id", "updatedDate", "createdDate", "deletedDate",
					"suppressed", "bibIds", "location", "status", "volumes", "barcode", "callNumber",
					"itemType", "transitInfo", "copyNo", "holdCount", "fixedFields", "varFields"))))
			.map(ResultSet::getEntries)
			.flatMapMany(Flux::fromIterable)
			.flatMap(result -> itemResultToItemMapper.mapResultToItem(result, hostLmsCode, bibId))
			.collectList();
	}

	@Override
	public Mono<Tuple2<String, String>> patronFind(String varFieldContent) {
		log.debug("patronFind({})", varFieldContent);

		return Mono.from(client.patronFind("u", varFieldContent))
			.doOnSuccess(result -> log.debug("the result of patronFind({})", result))
			.filter(result -> nonNull(result.getId()) && nonNull(result.getPatronType()))
			.map(this::returnPatronValues)
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when finding Patron: {}", error.getMessage());
				return Mono.empty();
			});
	}

	public Tuple2<String, String> returnPatronValues(SierraPatronRecord record) {
		return Tuples.of(valueOf(record.getId()), valueOf(record.getPatronType()));
	}

	@Override
	public Mono<String> createPatron(Patron patron) {
		log.debug("postPatron({})", patron);

		final var patronPatch = PatronPatch.builder()
			.patronType(parseInt(patron.getLocalPatronType()))
			.uniqueIds(Objects.requireNonNullElseGet(patron.getUniqueIds(), Collections::emptyList))
			// Unique IDs are used for names to avoid transmission of personally identifiable information
			.names(Objects.requireNonNullElseGet(patron.getUniqueIds(), Collections::emptyList))
			.barcodes(Objects.requireNonNullElseGet(patron.getLocalBarcodes(), Collections::emptyList))
			.build();

		return Mono.from(client.patrons(patronPatch))
			.doOnSuccess(result -> log.debug("the result of createPatron({})", result))
			.map(patronResult -> deRestify(patronResult.getLink()))
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when creating Patron: {}", error.getMessage());
				return Mono.error(new RuntimeException("Error occurred when creating Patron"));
			});
	}

	@Override
	public Mono<String> createBib(Bib bib) {
		log.debug("createBib(bib: {})", bib);
		
		// Setting fixedField 031 to n which indicates that the record should be suppressed from discovery.
		final var fixedFields = Map.of(31, FixedField.builder().label("suppress").value("n").build());
		final var authors = (bib.getAuthor() != null) ? List.of(bib.getAuthor()) : null;
		final var titles = (bib.getTitle() != null) ? List.of(bib.getTitle()) : null;

		final var bibPatch = BibPatch.builder()
			.authors(authors)
			.titles(titles)
			.fixedFields(fixedFields)
			.build();

		return Mono.from(client.bibs(bibPatch))
			.doOnSuccess(result -> log.debug("the result of createBib({})", result))
			.map(bibResult -> deRestify( bibResult.getLink()))
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when creating Bib: {}", error.getMessage());
				return Mono.error(new RuntimeException("Error occurred when creating Bib"));
			});
	}

	@Override
	public Mono<Tuple2<String, String>> placeHoldRequest(
		String id, 
		String recordType,
		String recordNumber, 
		String pickupLocation,
		String note,
		String patronRequestId
		) {

		PatronHoldPost patronHoldPost = new PatronHoldPost();
		patronHoldPost.setRecordType(recordType);
		patronHoldPost.setRecordNumber( convertToInteger(recordNumber) );
		patronHoldPost.setPickupLocation(pickupLocation);
		patronHoldPost.setNote(note);
		log.debug("placeHoldRequest({}...) {}", id,patronHoldPost);

		// Ian: NOTE... SIERRA needs time between placeHoldRequest and getPatronHoldRequestId completing... Either
		// we need retries or a delay.
		return Mono.from(client.placeHoldRequest(id, patronHoldPost))
			.then(
				Mono.defer(() -> getPatronHoldRequestId(id, recordNumber, note, patronRequestId))
				.retry(25)
			)
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when creating Hold: {}", error.getMessage());
				return Mono.error(new RuntimeException("Error occurred when creating Hold"));
			});
	}

	private boolean shouldIncludeHold(SierraPatronHold hold, String patronRequestId) {
		if ( ( hold != null ) && ( hold.note() != null ) && ( hold.note().contains(patronRequestId) ) ) {
			return true;
		}
		return false;
	}

	private Mono<Tuple2<String, String>> getPatronHoldRequestId(String patronLocalId, String localItemId, String note, String patronRequestId) {
		log.debug("getPatronHoldRequestId({}, {}, {}, {})", patronLocalId, localItemId, note, patronRequestId);

		// Ian: TEMPORARY WORKAROUND - Wait for sierra to process the hold and make it visible
		synchronized(this) {
			try {
				Thread.sleep(1000);
			} catch ( Exception e ) {
			}
		}

		return Mono.from(client.patronHolds(patronLocalId))
			.map(SierraPatronHoldResultSet::entries)
			.doOnNext(entries -> log.debug("Hold entries: {}",entries))
			.flatMapMany(Flux::fromIterable)
			.filter(hold -> shouldIncludeHold(hold, patronRequestId) )
			.collectList()
			.map(filteredHolds -> chooseHold(note, filteredHolds))
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when getting Hold: {}", error.getMessage());
				return Mono.error(new RuntimeException("Error occurred when getting Hold"));
			});
	}

	// Informed by https://techdocs.iii.com/sierraapi/Content/zObjects/holdObjectDescription.htm
	private String mapSierraHoldStatusToDCBHoldStatus(String code) {
		String result;

		switch (code) {
			case "0" -> result = HostLmsHold.HOLD_PLACED;
			case "b" -> result = HostLmsHold.HOLD_READY; // Bib ready for pickup
			case "j" -> result = HostLmsHold.HOLD_READY; // volume ready for pickup
			case "i" -> result = HostLmsHold.HOLD_READY; // Item ready for pickup
			case "t" -> result = HostLmsHold.HOLD_TRANSIT; // IN Transit
			default -> result = code;
		}

		return result;
	}
	private Tuple2<String, String> chooseHold(String note, List<SierraPatronHold> filteredHolds) {
		log.debug("chooseHold({},{})", note, filteredHolds);

		if (filteredHolds.size() == 1) {
			final String extractedId = deRestify(filteredHolds.get(0).id());
			final String localStatus = mapSierraHoldStatusToDCBHoldStatus(filteredHolds.get(0).status().code());

			return Tuples.of(extractedId, localStatus);
		} else if (filteredHolds.size() > 1) {
			throw new RuntimeException("Multiple hold requests found for the given note: " + note);
		} else {
			throw new RuntimeException("No hold request found for the given note: " + note);
		}
	}

	public static int convertToInteger(String integer) {
		try {
			return parseInt(integer);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid integer: " + integer, e);
		}
	}

	@Override
	public String getName() {
		return lms.getName();
	}

	@Override
	public boolean isEnabled() {
		return MapUtils.getAsOptionalString(lms.getClientConfig(), "ingest").map(StringUtils::isTrue).orElse(Boolean.TRUE);
	}

	public UUID uuid5ForBranch(@NotNull final String hostLmsCode, @NotNull final String localBranchId) {

		final String concat = UUID5_PREFIX + ":BRANCH:" + hostLmsCode + ":" + localBranchId;
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	public UUID uuid5ForLocation(@NotNull final String hostLmsCode, @NotNull final String localBranchId,
		@NotNull final String locationCode) {
		final String concat = UUID5_PREFIX + ":LOC:" + hostLmsCode + ":" + localBranchId + ":" + locationCode;
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	public UUID uuid5ForPickupLocation(@NotNull final String hostLmsCode, @NotNull final String locationCode) {
		final String concat = UUID5_PREFIX + ":SL:" + hostLmsCode + ":" + locationCode;
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public RawSourceRepository getRawSourceRepository() {
		return rawSourceRepository;
	}

	private ConfigurationRecord mapSierraBranchToBranchConfigurationRecord(BranchInfo bi) {
		List<LocationRecord> locations = new ArrayList<>();
		if (bi.locations() != null) {
			for (Map<String, String> sub_location : bi.locations()) {
				// It appears these are not only shelving locations but more general location records attached to the location
				locations.add(new LocationRecord(
					uuid5ForLocation(lms.getCode(), bi.id(), sub_location.get("code").trim()),
					sub_location.get("code").trim(), sub_location.get("name")));
			}
		}

		return BranchRecord.builder().id(uuid5ForBranch(lms.getCode(), bi.id()))
			.lms(lms).localBranchId(bi.id()).branchName(bi.name())
			.lat(bi.latitude() != null ? Float.valueOf(bi.latitude()) : null)
			.lon(bi.longitude() != null ? Float.valueOf(bi.longitude()) : null).subLocations(locations).build();
	}

	private ConfigurationRecord mapSierraPickupLocationToPickupLocationRecord(PickupLocationInfo pli) {
		return PickupLocationRecord.builder().id(uuid5ForPickupLocation(lms.getCode(), pli.code().trim())).lms(lms)
			.code(pli.code().trim()).name(pli.name()).build();
	}

	private Publisher<ConfigurationRecord> getBranches() {
		Iterable<String> fields = List.of("name", "address", "emailSource", "emailReplyTo", "latitude", "longitude",
			"locations");
		return Flux.from(client.branches(Integer.valueOf(100), Integer.valueOf(0), fields))
			.flatMap(results -> Flux.fromIterable(results.entries()))
			.map(result -> mapSierraBranchToBranchConfigurationRecord(result));
	}

	private Publisher<ConfigurationRecord> getPickupLocations() {
		return Flux.from(client.pickupLocations()).flatMap(results -> Flux.fromIterable(results))
			.map(result -> mapSierraPickupLocationToPickupLocationRecord(result));
	}

	private Publisher<ConfigurationRecord> getPatronMetadata() {
		return Flux.from(client.patronMetadata()).flatMap(results -> Flux.fromIterable(results))
			.flatMap(
				result -> Flux.fromIterable(result.values()).flatMap(item -> Mono.just(Tuples.of(item, result.field()))))
			.map(tuple -> mapSierraPatronMetadataToConfigurationRecord(tuple.getT1(), tuple.getT2()));
	}

	private ConfigurationRecord mapSierraPatronMetadataToConfigurationRecord(Map<String, Object> rdv, String field) {
		return RefdataRecord.builder()
			.category(field)
			.context(lms.getCode())
			.id(uuid5ForConfigRecord(field, rdv.get("code").toString().trim()))
			.lms(lms)
			.value(rdv.get("code").toString().trim())
			.label(rdv.get("desc").toString())
			.build();
	}

	private UUID uuid5ForConfigRecord(String field, String code) {
		final String concat = UUID5_PREFIX + ":RDV:" + lms.getCode() + ":" + field + ":" + code;
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
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

	@Override
	public Publisher<ConfigurationRecord> getConfigStream() {
		return Flux.from(getBranches()).concatWith(getPickupLocations()).concatWith(getPatronMetadata());
	}

	public TrackingRecord sierraPatronHoldToTrackingData(SierraPatronHold sph) {
		// log.debug("Convert {}",sph);
		TrackingRecord result = null;
		if (sph.patron().contains("@")) {
			// The patron identifier contains a % - this hold is either a supplier OR a
			// pickup Hold
			if (sph.record().contains("@")) {
				// The record contains a remote reference also - this is a pickup record
				result = PickupTrackingEvent.builder().hostLmsCode(lms.getCode()).build();
			} else {
				// This is a lender hold - shipping an item to a pickup location or direct to a
				// patron home
				result = LenderTrackingEvent.builder().hostLmsCode(lms.getCode()).normalisedRecordType(sph.recordType())
					.localHoldId(deRestify(sph.id())).localPatronReference(deRestify(sph.patron()))
					.localRecordId(deRestify(sph.record())).localHoldStatusCode(sph.status().code())
					.localHoldStatusName(sph.status().name())
					.pickupLocationCode(sph.pickupLocation() != null ? sph.pickupLocation().code() : null)
					.pickupLocationName(sph.pickupLocation() != null ? sph.pickupLocation().name() : null).build();
			}
		} else if (sph.record().contains("@")) {
			// patron does not contain % but record does - this is a request from a remote
			// site to
			// a patron at this system
			result = PatronTrackingEvent.builder().hostLmsCode(lms.getCode()).build();
		} else {
			// Hold record relates to internal activity and can be skipped
			// log.debug("No remote indications for this hold
			// {}/{}/{}",sph.patron(),sph.record(),sph.pickupLocation());
		}
		return result;
	}

	public Publisher<TrackingRecord> getTrackingData() {
		log.debug("getTrackingData");
		SierraPatronHoldResultSet init = new SierraPatronHoldResultSet(0, 0, new ArrayList<SierraPatronHold>());
		return Flux.just(init).expand(lastPage -> {
				log.debug("Fetch pages of data from offset {}", lastPage.start(), lastPage.total());
				Mono<SierraPatronHoldResultSet> pageMono = Mono
					.from(client.getAllPatronHolds(250, lastPage.start() + lastPage.entries().size()))
					.filter(m -> m.entries().size() > 0).switchIfEmpty(Mono.empty());
				// .subscribeOn(Schedulers.boundedElastic());
				return pageMono;
			}).flatMapIterable(page -> page.entries()) // <- prefer this to this ->.flatMapIterable(Function.identity())
			// Note to self: *Don't do this* it turns the expand above into an eager hot
			// publisher that will kill the system
			// .onBackpressureBuffer(100, null, BufferOverflowStrategy.ERROR)
			.flatMap(ri -> {
				TrackingRecord tr = sierraPatronHoldToTrackingData(ri);
				if (tr != null)
					return Mono.just(tr);
				else
					return Mono.empty();
			});
	}

	private Patron sierraPatronToHostLmsPatron(SierraPatronRecord spr) {
		log.debug("sierraPatronToHostLmsPatron({})",spr);
		String patronLocalAgency = null;

		// If we were supplied fixed fields, and we can find an entry for fixed field 158, grab the patron agency
		if ( ( spr.getFixedFields() != null ) &&
				 ( spr.getFixedFields().get(FIXED_FIELD_158) != null ) ) {
						patronLocalAgency = spr.getFixedFields().get(FIXED_FIELD_158).getValue().toString();
		}

		return Patron.builder()
			.localId(singletonList(valueOf(spr.getId())))
			.localPatronType(valueOf(spr.getPatronType()))
			.localBarcodes(spr.getBarcodes())
			.localNames(spr.getNames())
			.localHomeLibraryCode(spr.getHomeLibraryCode())
			.build();
	}

	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		log.debug("getPatronByLocalId({})", localPatronId);

		return Mono.from(client.getPatron(Long.valueOf(localPatronId)))
			.map(this::sierraPatronToHostLmsPatron)
			.switchIfEmpty(Mono.error(new RuntimeException("No patron found")));
	}

	@Override
	public Mono<Patron> updatePatron(String localPatronId, String patronType) {
		log.debug("updatePatron({})", localPatronId);

		final var patronPatch = PatronPatch.builder()
			.patronType(parseInt(patronType))
			.build();

		return Mono.from( client.updatePatron(Long.valueOf(localPatronId), patronPatch))
			.map(this::sierraPatronToHostLmsPatron)
			.switchIfEmpty(Mono.error(new RuntimeException("No patron found")));
	}

	public HostLmsHold sierraPatronHoldToHostLmsHold(SierraPatronHold sierraHold) {
		log.debug("sierraHoldToHostLmsHold({})", sierraHold);
		if ((sierraHold != null) && (sierraHold.id() != null)) {
			// Hold API sends back a hatheos style URI - we just want the hold ID
			String holdid = sierraHold.id().substring(sierraHold.id().lastIndexOf('/') + 1);

			// Map the hold status into a canonical value
			return new HostLmsHold(holdid,
					sierraHold.status() != null ? mapSierraHoldStatusToDCBHoldStatus(sierraHold.status().code()) : "");
		} else {
			return new HostLmsHold();
		}
	}

	public Mono<HostLmsHold> getHold(String holdId) {
		log.debug("getHold({})", holdId);
		return Mono.from(client.getHold(Long.valueOf(holdId)))
				.flatMap(sh -> Mono.just(sierraPatronHoldToHostLmsHold(sh)))
				.defaultIfEmpty(new HostLmsHold(holdId, "MISSING"));
	}

	//
	// II: We need to talk about this in a review session
	//
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs) {
		log.debug("updateItemStatus({},{})",itemId,crs);
		// See https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html#Standard
		// In Sierra "-" == AVAILABLE, !=ON_HOLDSHELF, $=BILLED PAID, m=MISSING, n=BILLED NOT PAID, z=CL RETURNED, o=Lib Use Only, t=In Transit
		String status = null;

		switch ( crs ) {
			case TRANSIT:
				status= "t";
				break;
		}

		if ( status != null ) {
			ItemPatch ip = ItemPatch.builder()
				.status(status)
				.build();
			return Mono.from(client.updateItem(itemId, ip))
				.thenReturn("OK");

		}
		else {
			return Mono.just("OK");
		}
	}

        public HostLmsItem sierraItemToHostLmsItem(SierraItem si){
                log.debug("convert {} to HostLmsItem",si);
                return HostLmsItem.builder()
                        .localId(si.getId())
                        .build();
        }

        public Mono<HostLmsItem> getItem(String itemId) {
		log.debug("getItem({})",itemId);
                return Mono.from(client.getItem(itemId))
                        .flatMap( sierraItem -> Mono.just(sierraItemToHostLmsItem(sierraItem)))
			.defaultIfEmpty(HostLmsItem.builder().localId(itemId).status("MISSING").build());
        }
}

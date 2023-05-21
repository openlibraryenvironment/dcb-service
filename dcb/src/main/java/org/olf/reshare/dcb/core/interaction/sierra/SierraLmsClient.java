package org.olf.reshare.dcb.core.interaction.sierra;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.reshare.dcb.utils.DCBStringUtilities.deRestify;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.validation.constraints.NotNull;

import org.marc4j.marc.Record;
import org.olf.reshare.dcb.configuration.BranchRecord;
import org.olf.reshare.dcb.configuration.ConfigurationRecord;
import org.olf.reshare.dcb.configuration.PickupLocationRecord;
import org.olf.reshare.dcb.configuration.RefdataRecord;
import org.olf.reshare.dcb.configuration.ShelvingLocationRecord;
import org.olf.reshare.dcb.core.ProcessStateService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.ingest.marc.MarcIngestSource;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.olf.reshare.dcb.ingest.model.RawSource;
import org.olf.reshare.dcb.storage.RawSourceRepository;
import org.olf.reshare.dcb.tracking.TrackingSource;
import org.olf.reshare.dcb.tracking.model.LenderTrackingEvent;
import org.olf.reshare.dcb.tracking.model.PatronTrackingEvent;
import org.olf.reshare.dcb.tracking.model.PickupTrackingEvent;
import org.olf.reshare.dcb.tracking.model.TrackingRecord;
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
import reactor.util.function.Tuples;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.bibs.BibResult;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.interaction.sierra.configuration.BranchInfo;
import services.k_int.interaction.sierra.configuration.PickupLocationInfo;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.interaction.sierra.holds.SierraPatronHoldResultSet;
import services.k_int.interaction.sierra.patrons.PatronPatch;
import services.k_int.interaction.sierra.patrons.Result;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

/**
 * See: https://sandbox.iii.com/iii/sierra-api/swagger/index.html
 * https://gitlab.com/knowledge-integration/libraries/reshare-dcb-service/-/raw/68fd93de0f84f928597481b16d2887bd7e58f455/dcb/src/main/java/org/olf/reshare/dcb/core/interaction/sierra/SierraLmsClient.java
 */
@Prototype
public class SierraLmsClient implements HostLmsClient, MarcIngestSource<BibResult>, TrackingSource {

	private static final Logger log = LoggerFactory.getLogger(SierraLmsClient.class);

	private static final String UUID5_PREFIX = "ingest-source:sierra-lms";
	private final ConversionService<?> conversionService = ConversionService.SHARED;
	private final HostLms lms;
	private final SierraApiClient client;
	private final ProcessStateService processStateService;
	private final RawSourceRepository rawSourceRepository;
	private final ItemResultToItemMapper itemResultToItemMapper = new ItemResultToItemMapper();

	public SierraLmsClient(@Parameter HostLms lms, HostLmsSierraApiClientFactory clientFactory,
			RawSourceRepository rawSourceRepository, ProcessStateService processStateService) {
		this.lms = lms;

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
//		log.info("Fetching batch from Sierra API with since={} offset={} limit={}", since, offset, limit);
		log.info("Creating subscribeable batch;  since={} offset={} limit={}", since, offset, limit);
		return Mono.from(client.bibs(params -> {
			params.deleted(false).offset(offset).limit(limit)
					.fields(List.of("id", "updatedDate", "createdDate", "deletedDate", "deleted", "marc", "suppressed"));

			if (since != null) {
				params.updatedDate(dtr -> {
					// dtr.to(LocalDateTime.now()).fromDate(LocalDateTime.from(since));
					dtr.to(LocalDateTime.now()).fromDate(since.atZone(java.time.ZoneId.of("UTC")).toLocalDateTime());
				});
			}
		}))
		.doOnSubscribe(_s -> log.info("Fetching batch from Sierra API with since={} offset={} limit={}", since, offset, limit));
	}

	/**
	 * Use the ProcessStateRepository to get the current state for
	 * <idOfLms>:"ingest" process - a list of name value pairs If we don't find one,
	 * just create a new empty map transform that data into the PublisherState class
	 * above ^^
	 */
	private Mono<PublisherState> getInitialState(UUID context, String process) {
		return processStateService.getStateMap(context, process)
				.defaultIfEmpty(new HashMap<String, Object>())
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
							generator_state.offset = Integer.parseInt(components[1]);
							log.info("Resuming bootstrap at offset " + generator_state.offset);
						} else if (components[0].equals("deltaSince")) {
							// Delta cursor is used after the initial bootstrap and lets us know the point
							// in time
							// from where we need to fetch records
							generator_state.sinceMillis = Long.parseLong(components[1]);
							generator_state.since = Instant.ofEpochMilli(generator_state.sinceMillis);
							if (components.length == 3) {
								// We're recovering from an interuption whilst processing a delta
								generator_state.offset = Integer.parseInt(components[2]);
							}
							log.info("Resuming delta at timestamp " + generator_state.since + " offset=" + generator_state.offset);
						}
					} else {
						log.info("Start a fresh ingest");
					}

					// Make a note of the time before we start
					generator_state.request_start_time = System.currentTimeMillis();
					log.debug("Create generator: offset={} since={}", generator_state.offset, generator_state.since);

					return generator_state;
				});
	}

	@Transactional(value = TxType.REQUIRES_NEW)
	protected Mono<PublisherState> saveState( PublisherState state ) {
		log.debug("Update state {}", state);
		
		return Mono.from(processStateService.updateState(lms.getId(), "ingest", state.storred_state))
				.thenReturn(state);
	}
	
	private Publisher<BibResult> pageAllResults(int pageSize) {

		return getInitialState(lms.getId(), "ingest")
			.flatMap(state -> Mono.zip(Mono.just(state.toBuilder().build()), fetchPage(state.since, state.offset, pageSize)))
			.expand(TupleUtils.function(( state ,results ) -> {
				
				var bibs = results.entries();
				log.info("Fetched a chunk of {} records", bibs.size());
				
//				state.current_page = bibs;
				
				log.info("got[{}] page of data, containing {} results", state.page_counter++, bibs.size());
				state.possiblyMore = bibs.size() == pageSize;

				// Increment the offset for the next fetch
				state.offset += bibs.size();
				
				// If we have exhausted the currently cached page, and we are at the end,
				// terminate.
				if (!state.possiblyMore) {
					log.info("Terminating cleanly - run out of bib results - new timestamp is {}", state.request_start_time);
					
					// Make a note of the time at which we started this run, so we know where
					// to pick up from next time
					state.storred_state.put("cursor", "deltaSince:" + state.request_start_time);

					log.info("No more results to fetch");
					return Mono.empty();
					
				} else {
					log.info("Exhausted current page, prep next");
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
					// Concatenate with the state so we can propogate signals from the save operation.
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

		// The stream of imported records.
		// return Flux.from(pageAllResults(since, 0, pageSize)).filter(sierraBib ->
		// sierraBib.marc() != null)
		// return Flux.from(pageAllResults(pageSize)).filter(sierraBib -> sierraBib.marc() != null)
    return Flux.from(pageAllResults(pageSize))
    		.filter(sierraBib -> sierraBib.marc() != null)
//				.subscribeOn(Schedulers.boundedElastic(), false)
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
	public Mono<List<Item>> getItemsByBibId(String bibId, String hostLmsCode) {
		log.debug("getItemsByBibId({})", bibId);

		return Flux.from(client.items(params -> params.deleted(false).bibIds(List.of(bibId))))
				.flatMap(results -> Flux.fromIterable(results.getEntries()))
				.map(result -> itemResultToItemMapper.mapResultToItem(result, hostLmsCode)).collectList();
	}

	public String extractIdFromUrl(String url) {
		// example: "https://sandbox.iii.com/iii/sierra-api/v6/patrons/2745325"
		int lastSlashIndex = url.lastIndexOf("/");
		return url.substring(lastSlashIndex + 1);
		}
	
	@Override
	public Mono<String> postPatron(String uniqueId, Integer patronType) {
	log.debug("postPatron({}, {})", uniqueId, patronType);

	final PatronPatch patronPatch = createPatronPatch( uniqueId, patronType );

	return Mono.from(client.patrons(patronPatch)).map(result -> {
			log.debug("the result of postPatron({})", result);
			return result;
		})
		.map(patronResult -> extractIdFromUrl( patronResult.getLink() ))
		.onErrorResume(NullPointerException.class, error -> {
			log.debug("NullPointerException occurred when creating Patron: {}", error.getMessage());
			return Mono.empty();
		});
	}

	private PatronPatch createPatronPatch(String uniqueId, Integer patronType) {
	PatronPatch patronPatch = new PatronPatch();
	patronPatch.setPatronType(patronType);
	patronPatch.setUniqueIds(new String[]{uniqueId});
	return patronPatch;
	}

	@Override
	public Mono<String> patronFind(String varFieldContent) {
	log.debug("patronFind({})", varFieldContent);

	return Mono.from(client.patronFind("u", varFieldContent))
		.map(result -> {
			log.debug("the result of patronFind({})", result);
			return result;
		})
		.map(Result::getId)
		.map(Object::toString)
		.onErrorResume(NullPointerException.class, error -> {
			log.debug("NullPointerException occurred when finding Patron: {}", error.getMessage());
			return Mono.empty();
		});
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

	public UUID uuid5ForShelvingLocation(@NotNull final String hostLmsCode, @NotNull final String localBranchId,
			@NotNull final String locationCode) {
		final String concat = UUID5_PREFIX + ":SL:" + hostLmsCode + ":" + localBranchId + ":" + locationCode;
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
		List<ShelvingLocationRecord> locations = new ArrayList<>();
		if (bi.locations() != null) {
			for (Map<String, String> shelving_location : bi.locations()) {
				// It appears these are not only shelving locations but more general location records attached to the location
				locations.add(new org.olf.reshare.dcb.configuration.ShelvingLocationRecord(
						uuid5ForShelvingLocation(lms.getCode(), bi.id(), shelving_location.get("code")),
						shelving_location.get("code"), shelving_location.get("name")));
			}
		}

		return BranchRecord.builder().id(uuid5ForBranch(lms.getCode(), bi.id()))
				.lms(lms).localBranchId(bi.id()).branchName(bi.name())
				.lat(bi.latitude() != null ? Float.valueOf(bi.latitude()) : null)
				.lon(bi.longitude() != null ? Float.valueOf(bi.longitude()) : null).shelvingLocations(locations).build();
	}

	private ConfigurationRecord mapSierraPickupLocationToPickupLocationRecord(PickupLocationInfo pli) {
		return PickupLocationRecord.builder().id(uuid5ForPickupLocation(lms.getCode(), pli.code())).lms(lms)
				.code(pli.code()).name(pli.name()).build();
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
			.id(uuid5ForConfigRecord(field, rdv.get("code").toString()))
			.lms(lms)
			.value(rdv.get("code").toString())
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


}

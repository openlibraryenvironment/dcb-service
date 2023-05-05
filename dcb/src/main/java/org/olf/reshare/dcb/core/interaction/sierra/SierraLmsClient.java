package org.olf.reshare.dcb.core.interaction.sierra;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

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
import reactor.core.scheduler.Schedulers;
import reactor.function.TupleUtils;
import reactor.util.function.Tuples;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.bibs.BibResult;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.interaction.sierra.configuration.BranchInfo;
import services.k_int.interaction.sierra.configuration.PickupLocationInfo;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.interaction.sierra.holds.SierraPatronHoldResultSet;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

import org.olf.reshare.dcb.tracking.TrackingSource;
import org.olf.reshare.dcb.tracking.model.TrackingRecord;

import reactor.core.publisher.BufferOverflowStrategy;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

/**
 * See: https://sandbox.iii.com/iii/sierra-api/swagger/index.html
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
			RawSourceRepository rawSourceRepository, ProcessStateService processStateService) {//, R2dbcOperations operations) {
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
		log.info("Fetching batch from Sierra API with since={} offset={} limit={}", since, offset, limit);
		return Mono.from(client.bibs(params -> {
			params.deleted(false).offset(offset).limit(limit)
					.fields(List.of("id", "updatedDate", "createdDate", "deletedDate", "deleted", "marc"));

			if (since != null) {
				params.updatedDate(dtr -> {
					// dtr.to(LocalDateTime.now()).fromDate(LocalDateTime.from(since));
					dtr.to(LocalDateTime.now()).fromDate(since.atZone(java.time.ZoneId.of("UTC")).toLocalDateTime());
				});
			}
		}));
	}

	/**
	 * Use the ProcessStateRepository to get the current state for
	 * <idOfLms>:"ingest" process - a list of name value pairs If we don't find one,
	 * just create a new empty map transform that data into the PublisherState class
	 * above ^^
	 */
	private Mono<PubisherState> getInitialState(UUID context, String process) {
		return processStateService.getStateMap(context, process).defaultIfEmpty(new HashMap<String, Object>())
				.map(current_state -> {

					PubisherState generator_state = new PubisherState(current_state, null);
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
	protected Mono<PubisherState> saveState( PubisherState state ) {
		log.debug("Update state " + lms.getId());
		
		return Mono.from(processStateService.updateState(lms.getId(), "ingest", state.storred_state))
				.thenReturn(state);
	}
	
	private Publisher<BibResult> pageAllResults(int limit) {

		return getInitialState(lms.getId(), "ingest")
			.flatMap(state -> Mono.zip(Mono.just(state), fetchPage(state.since, state.offset, limit)))
			.expand(TupleUtils.function(( state, results ) -> {
				
				var bibs = results.entries();
				log.info("Fetched a chunk of {} records", bibs.size());
				
				state.current_page = bibs;
				
				log.info("got[{}] page of data, containing {} results", state.page_counter++, bibs.size());
				state.possiblyMore = bibs.size() == limit;

				// Increment the offset for the next fetch
				state.offset += bibs.size();
				
				// If we have exhausted the currently cached page, and we are at the end,
				// terminate.
				if (!state.possiblyMore) {
					log.info("Terminating cleanly - run out of bib results - new timestamp is {}", state.request_start_time);
					
					// Make a note of the time at which we started this run, so we know where
					// to pick up from next time
					state.storred_state.put("cursor", "deltaSince:" + state.request_start_time);
					
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
				return saveState(state)
					.flatMap(updatedState -> {
						if (!state.possiblyMore) {
							log.info("No more results to fetch");
							return Mono.empty();
						}
						
						return Mono.just(updatedState).zipWith(fetchPage(updatedState.since, updatedState.offset, limit));
					});
			}))
			.map( tuple -> tuple.getT2() )
			.flatMap(results -> Flux.fromIterable(new ArrayList<>(results.entries())));
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
		return Flux.from(pageAllResults(pageSize)).filter(sierraBib -> sierraBib.marc() != null)
				.subscribeOn(Schedulers.boundedElastic()).onErrorResume(t -> {
					log.error("Error ingesting data {}", t.getMessage());
					t.printStackTrace();
					return Mono.empty();
				}).switchIfEmpty(Mono.just("No results returned. Stopping").mapNotNull(s -> {
					log.info(s);
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
		return IngestRecord.builder().uuid(uuid5ForBibResult(resource)).sourceSystem(lms).sourceRecordId(resource.id());
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
		return RefdataRecord.builder().category(field).context(lms.getCode())
				.id(uuid5ForConfigRecord(field, rdv.get("code").toString())).lms(lms).value(rdv.get("code").toString())
				.label(rdv.get("desc").toString()).build();
	}

	private UUID uuid5ForConfigRecord(String field, String code) {
		final String concat = UUID5_PREFIX + ":RDV:" + lms.getCode() + ":" + field + ":" + code;
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	class PubisherState {
		public Map<String, Object> storred_state;
		public List<BibResult> current_page;
		public boolean possiblyMore = false;
		public int offset = 0;
		public Instant since = null;
		public long sinceMillis = 0;
		public long request_start_time = 0;
		public boolean error = false;
		public int page_counter = 0;

		public PubisherState(Map<String, Object> storred_state, List<BibResult> current_page) {
			this.storred_state = storred_state;
			this.current_page = current_page;
		}
	}

	@Override
	public Publisher<ConfigurationRecord> getConfigStream() {
		return Flux.from(getBranches()).concatWith(getPickupLocations()).concatWith(getPatronMetadata());
	}

	public TrackingRecord sierraPatronHoldToTrackingData(SierraPatronHold sph) {
		log.debug("Convert {}",sph);
		return new TrackingRecord();
	}

    public Publisher<TrackingRecord> getTrackingData() {
	log.debug("getTrackingData");
	Integer o = Integer.valueOf(0);
        SierraPatronHoldResultSet init = new SierraPatronHoldResultSet(0,0,new ArrayList<SierraPatronHold>());
    	return Flux.just(init)
            .expand(lastPage -> {
		log.debug("Fetch pages of data from offset {}",lastPage.start(),lastPage.total());
                Mono<SierraPatronHoldResultSet> pageMono = Mono.from(client.getAllPatronHolds(250, lastPage.start()+lastPage.entries().size()))
                        .filter( m -> m.entries().size() > 0 )
                        .switchIfEmpty(Mono.empty());
                        // .subscribeOn(Schedulers.boundedElastic());
		log.debug("processing");
                return pageMono;
            })
            .flatMapIterable(page -> page.entries()) // <- prefer this to this ->.flatMapIterable(Function.identity())
	    // Note to self: *Don't do this* it turns the expand above into an eager hot publisher that will kill the system
            // .onBackpressureBuffer(100, null, BufferOverflowStrategy.ERROR)
	    .map( ri -> sierraPatronHoldToTrackingData(ri) )
	;
    }


}

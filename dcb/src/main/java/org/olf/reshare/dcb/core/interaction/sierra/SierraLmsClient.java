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
import org.olf.reshare.dcb.core.ProcessStateService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.Item;
import org.olf.reshare.dcb.core.interaction.Location;
import org.olf.reshare.dcb.core.interaction.Status;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.core.model.ProcessState;
import org.olf.reshare.dcb.ingest.marc.MarcIngestSource;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.olf.reshare.dcb.ingest.model.RawSource;
import org.olf.reshare.dcb.storage.RawSourceRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.BiConsumer;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.tree.JsonNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.bibs.BibResult;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.interaction.sierra.items.Result;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

import io.micronaut.data.r2dbc.operations.R2dbcOperations;

import reactor.core.scheduler.Schedulers;

@Prototype
public class SierraLmsClient implements HostLmsClient, MarcIngestSource<BibResult> {
	private static final Logger log = LoggerFactory.getLogger(SierraLmsClient.class);
	
	private static final int MAX_BUFFERED_ITEMS = 2000;
	
	private final ConversionService<?> conversionService = ConversionService.SHARED;

	private final HostLms lms;
	private final SierraApiClient client;
	private final ProcessStateService processStateService;
	private final R2dbcOperations operations;
	private final SierraResponseErrorMatcher sierraResponseErrorMatcher = new SierraResponseErrorMatcher();

	private final RawSourceRepository rawSourceRepository;

	public SierraLmsClient(@Parameter HostLms lms, 
                               HostLmsSierraApiClientFactory clientFactory, 
                               RawSourceRepository rawSourceRepository,
                               ProcessStateService processStateService,
                               R2dbcOperations operations
                               )  {
		this.lms = lms;

		// Get a sierra api client.
		client = clientFactory.createClientFor(lms);
		this.rawSourceRepository = rawSourceRepository;
		this.processStateService = processStateService;
		this.operations = operations;
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

	class PubisherState {
		public PubisherState(Map storred_state, List current_page) {
			this.storred_state = storred_state;
			this.current_page = current_page;
 		}
		public Map storred_state;
		public List<BibResult> current_page;
		public boolean possiblyMore = false;
		public int offset = 0;
		public Instant since = null;
		public long sinceMillis=0;
		public long request_start_time=0;
		public boolean error = false;
		public int page_counter = 0;
        }

	/**
	 * Use the ProcessStateRepository to get the current state for <idOfLms>:"ingest" process - a list of name value pairs
    	 * If we don't find one, just create a new empty map
	 * transform that data into the PublisherState class above ^^
	 */
	private Mono<PubisherState> getInitialState(UUID context, String process) {
		return processStateService.getStateMap(context, process)
                        .defaultIfEmpty(new HashMap<String,Object>())
			.map(current_state -> {

				PubisherState generator_state = new PubisherState(current_state, null);
				log.info("backpressureAwareBibResultGenerator - state="+current_state+" lmsid="+lms.getId()+" thread="+Thread.currentThread().getName());

				String cursor = (String) current_state.get("cursor");
				if ( cursor != null ) {
					log.debug("Cursor: "+cursor);
					String[] components = cursor.split(":");

					if ( components[0].equals("bootstrap") ) {
						// Bootstrap cursor is used for the initial load where we need to just page through everything
						// from day 0
						generator_state.offset = Integer.parseInt(components[1]);
						log.info("Resuming bootstrap at offset "+generator_state.offset);
					}
					else if ( components[0].equals("delta") )  {
						// Delta cursor is used after the initial bootstrap and lets us know the point in time
						// from where we need to fetch records
						generator_state.sinceMillis = Long.parseLong(components[1]);
						generator_state.since = Instant.ofEpochMilli(generator_state.sinceMillis);
						if ( components.length==3 ) {
							// We're recovering from an interuption whilst processing a delta
							generator_state.offset = Integer.parseInt(components[2]);
						}
						log.info("Resuming delta at timestamp "+generator_state.since+" offset="+generator_state.offset);
					}
				}
				else {
					log.info("Start a fresh ingest");
				}

				// Make a note of the time before we start
				generator_state.request_start_time = System.currentTimeMillis();
				log.debug("Create generator: offset={} since={}",generator_state.offset,generator_state.since);

				return generator_state;
                	});
	}




        private Consumer<PubisherState> stateConsumer() {
                return (state) -> {
                        log.debug("stateConsumer {}",state);
			operations.withTransaction(status ->
                            processStateService.updateState(lms.getId(),"ingest",state.storred_state)
                        );
			log.info("done updating state");
                };
        }

	private Publisher<BibResult> backpressureAwareBibResultGenerator(int limit) {

		// Start the process by loading the current state of the ingest process for this LMS id and creating a state object
     		// we can use in this generator. Flat map it and pass it into Flux.generate
		return getInitialState(lms.getId(), "ingest").flatMapMany(initialState ->
		Flux.generate(
			() -> initialState, (generator_state, sink) -> {
				// log.info("Generating - state="+state.storred_state);

				// If this is the first time through, or we have exhausted the current page get a new page of data
				if ( ( generator_state.current_page == null ) || ( generator_state.current_page.size() == 0 ) ) {
					// fetch a page of data and stash it
					log.info("Fetching["+generator_state.page_counter+"] a page, offset="+generator_state.offset+" limit="+limit+" thread="+Thread.currentThread().getName());
					BibResultSet bsr = fetchPage(generator_state.since, generator_state.offset, limit)
						.doOnError ( throwable -> log.warn("ONERROR fetching page", throwable) )
						.switchIfEmpty(Mono.just("No results returned. Stopping")
                                                .mapNotNull(s -> {
                                                        log.info(s);
                                                        return null;
                                                }))
						.share()
						.block();
					log.info("got page");
					if ( bsr != null ) {
						generator_state.current_page = bsr.entries();
						log.info("got["+(generator_state.page_counter++)+"] page of data");

						int number_of_records_returned = generator_state.current_page.size();
						if ( number_of_records_returned == limit ) {
							generator_state.possiblyMore = true;
 						}
						else {
							generator_state.possiblyMore = false;
						}
	
						// Increment the offset for the next fetch
						generator_state.offset += number_of_records_returned;
	
						log.info("Stashed a page of "+generator_state.current_page.size()+" records");
					}
					else {
						log.warn("ERRROR["+(generator_state.page_counter++)+"] No response from upstream server. Cancelling");
						
						generator_state.current_page = new ArrayList<BibResult>();
						// This will terminate the stream - by setting error=true we will leave the state intact
						// to be picked up on the next attempt
						generator_state.error = true;
					}
				}


				// log.info("Returning next - current size is "+generator_state.current_page.size());
				// Return the next pending bib result from the page we stashed
				
				if (generator_state.current_page.size() > 0 ) {
					sink.next(generator_state.current_page.remove(0));
				}

				// If we just consumed the last entry from the current page
				if (generator_state.current_page.size() == 0 ) {
					// If we have exhausted the currently cached page, and we are at the end, terminate.
					if ( generator_state.possiblyMore == false ) {
						log.info("Terminating cleanly - run out of bib results - new timestamp is {}",generator_state.request_start_time);
						// Make a note of the time at which we started this run, so we know where to pick up from
						// next time
						if ( !generator_state.error )
							generator_state.storred_state.put("cursor","deltaSince:"+generator_state.request_start_time);
						sink.complete();
					}
					else {
						log.info("Exhausted current page - update cursor and prep for loop");
						// We have finished consuming a page of data, but there is more to come. Remember
						// where we got up to and stash it in the DB
						if ( generator_state.since != null ) {
							generator_state.storred_state.put("cursor","deltaSince:"+generator_state.sinceMillis+":"+generator_state.offset);
						}
						else {
							generator_state.storred_state.put("cursor","bootstrap:"+generator_state.offset);
						}
					}
				}

				// pass the state at the end of this call to the next iteration
				// log.debug("return state "+state.storred_state);
				return generator_state;
			},
                        stateConsumer()
		)
		);
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
		// return Flux.from(pageAllResults(since, 0, pageSize)).filter(sierraBib -> sierraBib.marc() != null)
		return Flux.from(backpressureAwareBibResultGenerator(pageSize)).filter(sierraBib -> sierraBib.marc() != null)
				.subscribeOn(Schedulers.boundedElastic())
				.doOnError ( throwable -> log.warn("ONERROR fetching page", throwable) )
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
				.sourceSystem(lms)
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

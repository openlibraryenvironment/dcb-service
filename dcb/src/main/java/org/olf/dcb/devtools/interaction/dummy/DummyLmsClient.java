package org.olf.dcb.devtools.interaction.dummy;

import static java.lang.Integer.parseInt;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.utils.DCBStringUtilities.deRestify;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.validation.constraints.NotNull;

import org.marc4j.marc.Record;
import org.olf.dcb.configuration.BranchRecord;
import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.configuration.PickupLocationRecord;
import org.olf.dcb.configuration.RefdataRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.ingest.model.Identifier;
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
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsHold;
import org.olf.dcb.core.interaction.Bib;

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
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;
import reactor.util.retry.Retry;
import java.time.Duration;
import services.k_int.interaction.oai.records.OaiListRecordsMarcXML;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import io.micronaut.core.annotation.Nullable;
import java.io.StringWriter;

import org.olf.dcb.core.interaction.shared.PublisherState;



/**
 * This adapter exists to allow devs to run a fully functional local system without
 * configuring an external HostLMS.
 */
// public class DummyLmsClient implements HostLmsClient, MarcIngestSource<OaiListRecordsMarcXML> {
@Prototype
public class DummyLmsClient implements HostLmsClient, IngestSource {

	private static final Logger log = LoggerFactory.getLogger(DummyLmsClient.class);

	private static final String UUID5_PREFIX = "ingest-source:dummy-lms";
	private final ConversionService conversionService;
	private final HostLms lms;
	private final ProcessStateService processStateService;
	private final RawSourceRepository rawSourceRepository;

        private static final String[] titleWords = { "Science", "Philosophy", "Music", "Art", "Nonsense", "Dialectic", "Curiosity", "Reading", "Numeracy", "Literacy" };

	public DummyLmsClient(@Parameter HostLms lms, 
		RawSourceRepository rawSourceRepository, 
                ProcessStateService processStateService,
                ConversionService conversionService) {
		this.lms = lms;
		this.rawSourceRepository = rawSourceRepository;
		this.processStateService = processStateService;
		this.conversionService = conversionService;
	}

	@Override
	public HostLms getHostLms() {
		return lms;
	}

        public List<HostLmsPropertyDefinition> getSettings() {
                return List.of(
                        new HostLmsPropertyDefinition("base-url", "Base URL Of Dummy System", Boolean.TRUE, "URL" )
                );
        }

        public Flux<Map<String, ?>> getAllBibData() {
		return Flux.empty();
	}

        public Mono<List<Item>> getItemsByBibId(String bibId, String hostLmsCode) {
                // All Dummy systems return holdins for each shelving location
                log.debug("getItemsByBibId({},{})",bibId,hostLmsCode);
                String shelvingLocations = (String) lms.getClientConfig().get("shelving-locations");
                if ( shelvingLocations != null ) {
                        int n=0;
                        List<Item> result_items = new ArrayList();
                        String[] locs = shelvingLocations.split(",");
                        for ( String s : locs ) {
                                result_items.add(
                                        Item.builder() 
                                                .id(bibId+"-i"+n)
                                                .bibId(bibId)
                                                .status(new ItemStatus(ItemStatusCode.AVAILABLE))
                                                .hostLmsCode(lms.getCode())
                                                // .dueDate(parsedDueDate)
                                                .location(org.olf.dcb.core.model.Location.builder()
                                                .code(s)
                                                .name(s)
                                                .build())
                                                .barcode(bibId+"-i"+n)
                                                .callNumber("CN-"+bibId)
                                                .holdCount(0)
                                                .localItemType("Books/Monographs")
                                                .localItemTypeCode("BKM")
                                                .deleted(false)
                                                .suppressed(false)
                                                .build()
                                );
                                n++;
                        }

                        return Mono.just(result_items);
                }

	        return Mono.empty();
	}

        public Mono<Patron> getPatronByLocalId(String localPatronId) {
		return Mono.empty();
	}

        @Override
        public String getName() {
                return lms.getName();
        }                       
        
        @Override
        public boolean isEnabled() {
                return MapUtils.getAsOptionalString(lms.getClientConfig(), "ingest").map(StringUtils::isTrue).orElse(Boolean.TRUE);
        }       

        public UUID uuid5ForDummyRecord(@NotNull final String record_id) {
                final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + record_id;
                return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
        }

        public Mono<String> createPatron(Patron patron) {
		return Mono.empty();
	}

        public Mono<String> createBib(Bib bib) {
		return Mono.empty();
	}

	public Mono<Patron> updatePatron(String localId, String patronType) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret) {
		// TODO: needs implementing
		return null;
	}


	@Override
        public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
                log.debug("createItem({})",cic);
		return Mono.empty();
	}

	public Mono<HostLmsHold> getHold(String holdId) {
                log.debug("getHold({})",holdId);
		return Mono.empty();
	}

	public Mono<HostLmsItem> getItem(String itemId) {
                log.debug("getItem({})",itemId);
		return Mono.empty();
	}

	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs) {
		return Mono.just("Dummy");
	}

        // WARNING We might need to make this accept a patronIdentity - as different systems might take different ways to identify the patron
        public Mono<String> checkOutItemToPatron(String itemId, String patronBarcode) {
		return Mono.just("DUMMY");
	}

        public Mono<String> deleteItem(String id) {
                return Mono.just("DUMMY");
        }

        public Mono<String> deleteBib(String id) {
                return Mono.just("DUMMY");
        }


        @Override
        public Publisher<IngestRecord> apply(@Nullable Instant changedSince) {

		int pageSize=100;
                return getInitialState(lms.getId(), "ingest")
                        .flatMap(state -> Mono.zip(Mono.just(state.toBuilder().build()), fetchPage(state, pageSize)))
                        .expand(TupleUtils.function(( state ,bibs ) -> {


                		int target_record_count = ((Integer)(lms.getClientConfig().get("num-records-to-generate"))).intValue();
				int records_generated_so_far = Integer.valueOf(state.storred_state.get("num_generated").toString()).intValue();
				records_generated_so_far += bibs.size();
                                state.storred_state.put("num_generated", ""+records_generated_so_far);

                                state.possiblyMore = records_generated_so_far < target_record_count;

                                // Increment the offset for the next fetch
                                state.offset += bibs.size();

                                // If we have exhausted the currently cached page, and we are at the end,
                                // terminate.
                                if (!state.possiblyMore) {
                                        log.info("{} ingest Terminating cleanly - run out of bib results - new timestamp is {}", lms.getName(), state.request_start_time);
                                        return Mono.empty();

                                } else {
                                        log.info("Exhausted current page from {} , prep next", lms.getName());
                                }

                                // Create a new mono that first saves the state and then uses it to fetch another page.
                                return Mono.just(state.toBuilder().build()) // toBuilder().build() should copy the object.
                                        .zipWhen( updatedState -> fetchPage(updatedState, pageSize));
                        }))
                        .concatMap( TupleUtils.function((state, page) -> {
                                return Flux.fromIterable(page)
                                        // Concatenate with the state so we can propagate signals from the save operation.
                                        .concatWith(Mono.defer(() ->
                                                        saveState(state))
                                                .flatMap(_s -> {
                                                        log.debug("Updating state...");
                                                        return Mono.empty();
                                                }))

                                        .doOnComplete(() -> log.debug("Consumed {} items", page.size()));
                        }));

        }

        private Mono<List<IngestRecord>> fetchPage(PublisherState state, int limit) {

                log.debug("fetchPage... {},{},{}",state,limit);
                log.debug("fetchPage... config={}",lms.getClientConfig());

                int target = ((Integer)(lms.getClientConfig().get("num-records-to-generate"))).intValue();
		int records_generated_so_far = Integer.valueOf(state.storred_state.get("num_generated").toString()).intValue();

		log.debug("target: {}, current:{}",target,records_generated_so_far);

		List<IngestRecord> result = new ArrayList();

		if ( records_generated_so_far == 0 ) {
			log.debug("Bootstrap a dummy collection with some reasonable records");
			generateRealRecords(result);
		}

		// Then bulk out the collection with generated records
		for ( int n=result.size(); ( ( n<limit ) && ( (records_generated_so_far+n) < target ) ) ; n++ ) {
                        String str_record_id = ""+(1000000+(n+records_generated_so_far));
			result.add(createDummyBookRecord(str_record_id, str_record_id, generateTitle(str_record_id)));
		}

		return Mono.just(result);
	}

	private void generateRealRecords(List<IngestRecord> result) {
		log.debug("Adding in real records");
		result.add(createDummyBookRecord("0000001","978-0471948391","Brain of the Firm 2e: 10 (Classic Beer Series)"));
	}

	private IngestRecord createDummyBookRecord(String str_record_id, String isbn13, String title) {
                UUID rec_uuid = uuid5ForDummyRecord(str_record_id);

                Map<String, Object> canonicalMetadata = new HashMap();
                canonicalMetadata.put("title",title);

		Set<Identifier> identifiers = new HashSet();
		identifiers.add(Identifier.builder().namespace("isbn").value(isbn13).build());

		return IngestRecord.builder()
                                        .uuid(rec_uuid)
                                        .sourceSystem(lms)
                                        .sourceRecordId(str_record_id)
                                        .identifiers(identifiers)
                                        .canonicalMetadata(canonicalMetadata)
                                        .derivedType("Books")
                                        .build();
	}

        @Override
        public Publisher<ConfigurationRecord> getConfigStream() {
                return Mono.empty();
        }

        @Override
        public Mono<Tuple2<String, String>> placeHoldRequest(
                String id,
                String recordType,
                String recordNumber,
                String pickupLocation,
                String note,
                String patronRequestId) {
                return Mono.empty();
        }

        private String generateTitle(String recordId) {
                StringWriter sw = new StringWriter();

                for ( int i=0; i<recordId.length(); i++ ) {
                        if ( i > 0 )
                                sw.write(" ");
                        // conver the char at position i into an integer of that value 0-9
                        int digit = recordId.charAt(i)-48;
                        sw.write(titleWords[digit]);
                }
                return sw.toString();
        }


	
        /**
         * Use the ProcessStateRepository to get the current state for
         * <idOfLms>:"ingest" process - a list of name value pairs If we don't find one,
         * just create a new empty map transform that data into the PublisherState class
         * above ^^
         * THIS SHOULD REALLY MOVE TO A SHARED SUPERCLASS
         */
        private Mono<PublisherState> getInitialState(UUID context, String process) {
                return processStateService.getStateMap(context, process)
                        .defaultIfEmpty(new HashMap<>())
                        .map(current_state -> {
				PublisherState generator_state = new PublisherState(current_state);
                                if ( current_state.get("num_generated") == null ) {
					current_state.put("num_generated", Long.valueOf(0));
                                }
                                generator_state.request_start_time = System.currentTimeMillis();
                                return generator_state;
                        });
        }

        @Transactional(value = TxType.REQUIRES_NEW)
        protected Mono<PublisherState> saveState(PublisherState state) {
                log.debug("Update state {} - {}", state,lms.getName());

                return Mono.from(processStateService.updateState(lms.getId(), "ingest", state.storred_state))
                        .thenReturn(state);
        }

}

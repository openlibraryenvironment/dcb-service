package org.olf.dcb.devtools.interaction.dummy;

import static java.lang.Integer.parseInt;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.utils.DCBStringUtilities.deRestify;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

                log.debug("apply... {},{}",changedSince,lms.getClientConfig().toString());

                // Lets generate 100 records to begin with
                Integer i = (Integer)(lms.getClientConfig().get("num-records-to-generate"));
                if ( i == null )
                  i = Integer.valueOf(0);

                final Integer num_records = i;

                return Flux.generate(() -> Integer.valueOf(0), (state, sink) -> {
                        if ( state.intValue() < num_records.intValue() ) {
                                // We number generated records starting at 1000000 to give us space to return any
                                // manually defined test records first.
                                log.debug("Generate record {}",state.intValue());

                                String str_record_id = ""+(1000000+(state.intValue()));
                                Map<String, Object> canonicalMetadata = new HashMap();
                                canonicalMetadata.put("title",generateTitle(str_record_id));
                                UUID rec_uuid = uuid5ForDummyRecord(str_record_id);

                                IngestRecord next_ingest_record = IngestRecord.builder()
                                        .uuid(rec_uuid)
                                        .sourceSystem(lms)
                                        .sourceRecordId(str_record_id)
                                        .canonicalMetadata(canonicalMetadata)
                                        .derivedType("Books")
                                        .build();
                                sink.next(next_ingest_record);
                        }
                        else {
                                sink.complete();
                        }
                        return Integer.valueOf(state.intValue() + 1);
                });
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
                                log.info("state=" + current_state + " lmsid=" + lms.getId() + " thread="+ Thread.currentThread().getName());

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

}

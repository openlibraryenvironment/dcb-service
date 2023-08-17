package org.olf.dcb.core.interaction.folio;

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

import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.validation.constraints.NotNull;

import org.marc4j.marc.Record;
import org.olf.dcb.configuration.BranchRecord;
import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.configuration.PickupLocationRecord;
import org.olf.dcb.configuration.RefdataRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.HostLmsClient;
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

// import services.k_int.interaction.folio.FolioApiClient;

import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;
import reactor.util.retry.Retry;
import java.time.Duration;

import services.k_int.interaction.oai.records.OaiListRecordsMarcXML;
import org.olf.dcb.core.interaction.CreateItemCommand;


/**
 */
@Prototype
public class FolioLmsClient implements HostLmsClient, MarcIngestSource<OaiListRecordsMarcXML> {

	private static final Logger log = LoggerFactory.getLogger(FolioLmsClient.class);

	private static final String UUID5_PREFIX = "ingest-source:folio-lms";
	private final ConversionService<?> conversionService = ConversionService.SHARED;
	private final HostLms lms;
	// private final FolioApiClient client;
	private final ProcessStateService processStateService;
	private final RawSourceRepository rawSourceRepository;

	public FolioLmsClient(@Parameter HostLms lms, 
		RawSourceRepository rawSourceRepository, 
                ProcessStateService processStateService) {
		this.lms = lms;

		// Get a sierra api client.
		// client = clientFactory.createClientFor(lms);
		this.rawSourceRepository = rawSourceRepository;
		this.processStateService = processStateService;
	}

	@Override
	public HostLms getHostLms() {
		return lms;
	}

        public Flux<Map<String, ?>> getAllBibData() {
		return Flux.empty();
	}

        public Mono<List<Item>> getItemsByBibId(String bibId, String hostLmsCode) {
		return Mono.empty();
	}

        // public Mono<String> createPatron(String uniqueId, String patronType) {
	// 	return Mono.empty();
	// }

        public Mono<Tuple2<String, String>> patronFind(String uniqueId) {
		return Mono.empty();
	}

        // (localHoldId, localHoldStatus)
        public Mono<Tuple2<String, String>> placeHoldRequest(
                String id,
                String recordType,
                String recordNumber,
                String pickupLocation,
                String note,
                String patronRequestId) {
		return Mono.empty();
	}


        public Mono<Patron> getPatronByLocalId(String localPatronId) {
		return Mono.empty();
	}

        @Override
        public RawSourceRepository getRawSourceRepository() {
                return rawSourceRepository;
        }

        @Override
        public Record resourceToMarc(OaiListRecordsMarcXML resource) {
                return null;
        }

        @Override
        public IngestRecordBuilder initIngestRecordBuilder(OaiListRecordsMarcXML resource) {

                // Use the host LMS as the
                return IngestRecord.builder()
                        .uuid(uuid5ForOAIResult(resource))
                        .sourceSystem(lms)
                        .sourceRecordId(resource.id())
                        .suppressFromDiscovery(resource.suppressed())
                        .deleted(resource.deleted());
        }

        @Override
        public Publisher<OaiListRecordsMarcXML> getResources(Instant since) {
		return Flux.empty();
	}

        @Override
        @NotNull
        public String getDefaultControlIdNamespace() {
                return lms.getName();
        }

        @Override
        public Publisher<ConfigurationRecord> getConfigStream() {
                return Flux.empty();
        }

        @Override
        public String getName() {
                return lms.getName();
        }                       
        
        @Override
        public boolean isEnabled() {
                return MapUtils.getAsOptionalString(lms.getClientConfig(), "ingest").map(StringUtils::isTrue).orElse(Boolean.TRUE);
        }       

	// Privatesu

	// This should convert whatever type the FOLIO source returns to a RawSource
        @Override
        public RawSource resourceToRawSource(OaiListRecordsMarcXML resource) {

                // final JsonNode rawJson = conversionService.convertRequired(resource.marc(), JsonNode.class);

                // @SuppressWarnings("unchecked")
                // final Map<String, ?> rawJsonString = conversionService.convertRequired(rawJson, Map.class);

                // RawSource raw = RawSource.builder().id(uuid5ForRawJson(resource)).hostLmsId(lms.getId()).remoteId(resource.id())
                //         .json(rawJsonString).build();

                // return raw;
		return null;
        }

        public UUID uuid5ForOAIResult(@NotNull final OaiListRecordsMarcXML result) {
                final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + result.id();
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
        public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		return Mono.empty();
	}

	public Mono<HostLmsHold> getHold(String holdId) {
		return Mono.empty();
	}

	public Mono<HostLmsItem> getItem(String itemId) {
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

}

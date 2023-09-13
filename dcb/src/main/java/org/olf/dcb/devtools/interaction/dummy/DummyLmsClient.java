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

import io.micronaut.core.annotation.Nullable;



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
	public Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret) {
		// TODO: needs implementing
		return null;
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


        @Override
        public Publisher<IngestRecord> apply(@Nullable Instant changedSince) {
                return Mono.empty();
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
}

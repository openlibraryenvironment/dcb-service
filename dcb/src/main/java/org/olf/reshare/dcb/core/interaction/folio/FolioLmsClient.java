package org.olf.reshare.dcb.core.interaction.folio;

import static java.lang.Integer.parseInt;
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
import org.olf.reshare.dcb.core.interaction.HostLmsPatronDTO;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.ingest.marc.MarcIngestSource;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.olf.reshare.dcb.ingest.model.RawSource;
import org.olf.reshare.dcb.storage.RawSourceRepository;
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
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
// import services.k_int.interaction.folio.FolioApiClient;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;
import reactor.util.retry.Retry;
import java.time.Duration;

/**
 * See: https://sandbox.iii.com/iii/sierra-api/swagger/index.html
 * https://gitlab.com/knowledge-integration/libraries/reshare-dcb-service/-/raw/68fd93de0f84f928597481b16d2887bd7e58f455/dcb/src/main/java/org/olf/reshare/dcb/core/interaction/sierra/SierraLmsClient.java
 */
@Prototype
public class FolioLmsClient implements HostLmsClient {

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

        public Mono<String> createPatron(String uniqueId, String patronType) {
		return Mono.empty();
	}

        public Mono<String> patronFind(String uniqueId) {
		return Mono.empty();
	}

        public Mono<Tuple2<String, String>> placeHoldRequest(String id, String recordType, String recordNumber, String pickupLocation) {
		return Mono.empty();
	}

        public Mono<HostLmsPatronDTO> getPatronByLocalId(String localPatronId) {
		return Mono.empty();
	}

	// Privates


}

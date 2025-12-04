package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.unexpectedResponseProblem;
import static org.olf.dcb.core.interaction.polaris.Direction.POLARIS_TO_HOST_LMS;
import static org.olf.dcb.core.interaction.polaris.MarcConverter.convertToMarcRecord;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.AVAILABLE;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.UUID5_PREFIX;
import static org.olf.dcb.core.interaction.polaris.PolarisItem.mapItemStatus;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;
import static services.k_int.utils.ReactorUtils.raiseError;
import static services.k_int.utils.StringUtils.parseList;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import org.marc4j.marc.Record;
import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.events.RulesetCacheInvalidator;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.DeleteCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.HostLmsRenewal;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.PreventRenewalCommand;
import org.olf.dcb.core.interaction.RelativeUriResolver;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.LibraryHold;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronCirculationBlocksResult;
import org.olf.dcb.core.interaction.polaris.exceptions.HoldRequestException;
import org.olf.dcb.core.interaction.shared.MissingParameterException;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.NumericPatronTypeMapper;
import org.olf.dcb.core.interaction.shared.PublisherState;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.dataimport.job.SourceRecordDataSource;
import org.olf.dcb.dataimport.job.SourceRecordImportChunk;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.ingest.marc.MarcIngestSource;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.ingest.model.RawSource;
import org.olf.dcb.rules.ObjectRulesService;
import org.olf.dcb.rules.ObjectRuleset;
import org.olf.dcb.storage.RawSourceRepository;
import org.reactivestreams.Publisher;
import org.zalando.problem.Problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.json.tree.JsonArray;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.retry.Retry;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

@Slf4j
@Prototype
public class PolarisLmsClient implements MarcIngestSource<PolarisLmsClient.BibsPagedRow>, HostLmsClient, SourceRecordDataSource {
	private final URI defaultBaseUrl;
	private final URI applicationServicesOverrideURL;
	private final HostLms lms;
	private final HttpClient client;
	private final ProcessStateService processStateService;
	private final RawSourceRepository rawSourceRepository;
	private final ConversionService conversionService;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final NumericPatronTypeMapper numericPatronTypeMapper;
	private final IngestHelper ingestHelper;
	private final PolarisItemMapper itemMapper;
	private final PAPIClient PAPIService;
	private final ApplicationServicesClient ApplicationServices;
	private final List<ApplicationServicesClient.MaterialType> materialTypes = new ArrayList<>();
	private final List<PolarisItemStatus> statuses = new ArrayList<>();
	private final ObjectMapper objectMapper;
	private final ObjectRulesService objectRuleService;
	private final RulesetCacheInvalidator cacheInvalidator;
	private final HostLmsService hostLmsService;

	// ToDo align these URLs
  private static final URI ERR0211 = URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/0211/Polaris/UnableToCreateItem");
	private static final String DCB_BORROWING_FLOW = "DCB";
	private static final String ILL_BORROWING_FLOW = "ILL";
	private final PolarisConfig polarisConfig;
	
	private final R2dbcOperations r2dbcOperations;
	private final Pattern msDateRegex;

	@Creator
	PolarisLmsClient(@Parameter("hostLms") HostLms hostLms, @Parameter("client") HttpClient client,
									 ProcessStateService processStateService, RawSourceRepository rawSourceRepository,
									 ConversionService conversionService, ReferenceValueMappingService referenceValueMappingService,
									 NumericPatronTypeMapper numericPatronTypeMapper, PolarisItemMapper itemMapper,
									 R2dbcOperations r2dbcOperations, ObjectMapper objectMapper,
									 ObjectRulesService objectRuleService, RulesetCacheInvalidator cacheInvalidator, HostLmsService hostLmsService) {

		log.debug("Creating Polaris HostLms client for HostLms {}", hostLms);

		this.lms = hostLms;
		this.objectMapper = objectMapper;
		this.conversionService = conversionService;
		this.cacheInvalidator = cacheInvalidator;
		this.hostLmsService = hostLmsService;
		this.polarisConfig = convertConfig(hostLms);
		this.defaultBaseUrl = UriBuilder.of(polarisConfig.getBaseUrl()).build();
		this.applicationServicesOverrideURL = applicationServicesOverrideURL();
		this.ApplicationServices = new ApplicationServicesClient(this, polarisConfig);
		this.PAPIService = new PAPIClient(this, polarisConfig, conversionService, lms);
		this.itemMapper = itemMapper;
		this.ingestHelper = new IngestHelper(this, hostLms, processStateService);
		this.processStateService = processStateService;
		this.rawSourceRepository = rawSourceRepository;
		this.referenceValueMappingService = referenceValueMappingService;
		this.numericPatronTypeMapper = numericPatronTypeMapper;
		this.client = client;
		this.r2dbcOperations = r2dbcOperations;
		this.objectRuleService = objectRuleService;
		this.msDateRegex = Pattern.compile("/date\\((\\d+)([+-]\\d{4})\\)/");
	}

	private PolarisConfig convertConfig(HostLms hostLms) {
		Map<String, Object> lmsConf = hostLms.getClientConfig();

		try {
			JsonNode conftree = objectMapper.writeValueToTree(lmsConf);
			PolarisConfig conf = objectMapper.readValueFromTree(conftree, PolarisConfig.class);
			return conf;
		} catch (IOException e) {
			throw new RuntimeException( e );
		}
	}

	private URI applicationServicesOverrideURL() {
		return polarisConfig.getOverrideBaseUrl() != null
			? UriBuilder.of(polarisConfig.getOverrideBaseUrl()).build()
			: null;
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(
		PlaceHoldRequestParameters parameters) {

		return placeHoldRequest(parameters, false);
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters) {
		// PUA - the borrower pickup location is outside the system so use the ILL location
		// this is how we place supplier holds. Following the same pattern here
		if (isFollowingWorkflow(parameters, PICKUP_ANYWHERE_WORKFLOW)) {
			return placeHoldRequest(parameters, false);
		}

		final var borrowerLendingFlow = borrowerlendingFlow();
		if (borrowerLendingFlow == null) {
			return placeHoldRequest(parameters, TRUE);
		}

		return switch (borrowerLendingFlow) {
			case DCB_BORROWING_FLOW -> placeHoldRequest(parameters, TRUE);
			case ILL_BORROWING_FLOW -> placeILLHoldRequest(polarisConfig.getIllLocationId(), parameters);
			default -> placeHoldRequest(parameters, TRUE);
		};
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtPickupAgency({})", parameters);

		return placeHoldRequest(parameters, true);
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtLocalAgency({})", parameters);
		return placeHoldRequestAtBorrowingAgency(parameters);
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal hostLmsRenewal) {
		log.info("renew({})", hostLmsRenewal);

		return PAPIService.itemCheckoutPost(hostLmsRenewal.getLocalItemBarcode(), hostLmsRenewal.getLocalPatronBarcode())
			.map(itemCheckoutResult -> hostLmsRenewal)
			.doOnError(e -> log.error("Error renewing item", e));
	}

	@Override
	public Mono<LocalRequest> updateHoldRequest(LocalRequest localRequest) {

		log.info("updatePatronRequest({})", localRequest);

		final var itemId = localRequest.getRequestedItemId();

		// holds get deleted in polaris after being filled
		// adding a note to help staff know where to send the item back
		final var supplierAgencyCode = getValueOrNull(localRequest, LocalRequest::getSupplyingAgencyCode);
		final var supplierHostLmsCode = getValueOrNull(localRequest, LocalRequest::getSupplyingHostLmsCode);
		final String noteForStaff = getNoteForStaff(supplierAgencyCode, supplierHostLmsCode);

		return Mono.zip(
				ApplicationServices.itemrecords(itemId, FALSE),
				getMappedItemType(localRequest.getCanonicalItemType())
			)
			.doOnSuccess(item -> log.info("Successfully received item record: {}", item))
			.doOnError(error -> log.error("Error retrieving item record: {}", error.getMessage()))
			.flatMap(tuple -> {
				final var item = tuple.getT1();
				final var itemType = Integer.valueOf(tuple.getT2());
				final var itemStatusId = item.getItemStatusID();
				final var newBarcode = localRequest.getRequestedItemBarcode();

				return ApplicationServices.updateItemRecord(itemId, itemStatusId, newBarcode, noteForStaff, itemType);
			})
			.doOnSuccess(item -> log.info("Successfully updated item, returning {}.", item))
			.doOnError(error -> log.error("Error updating item: {}", error.getMessage()))
			.then(Mono.defer(() -> getRequest(HostLmsRequest.builder().localId(localRequest.getLocalId()).build())))
			.map(hostLmsRequest -> LocalRequest.builder()
				.localId(hostLmsRequest.getLocalId())
				.localStatus(hostLmsRequest.getStatus())
				.rawLocalStatus(hostLmsRequest.getRawStatus())
				.requestedItemId(hostLmsRequest.getRequestedItemId())
				.build());
	}

	private String borrowerlendingFlow() {
		return polarisConfig.getBorrowerLendingFlow();
	}

	private Mono<LocalRequest> placeILLHoldRequest(Integer illLocationId, PlaceHoldRequestParameters parameters) {
		log.info("placeILLHoldRequest {}", parameters);

		final var patronLocalId = parameters.getLocalPatronId();
		final var title = "DCB-" + parameters.getTitle();
		final var pickupLocation = getPickupLocation(parameters, TRUE);
		final var note = parameters.getNote();

		final var createHoldParams = HoldRequestParameters.builder()
			.localPatronId(patronLocalId)
			.title(title)
			.pickupLocation(pickupLocation)
			.note(note)
			// TODO: change this to it's own field
			.localItemLocationId(illLocationId)
			.build();

		return ApplicationServices.createILLHoldRequestWorkflow(createHoldParams)
			.doOnNext( hr -> log.info("got hold response {}",hr) )
			.flatMap(function(this::getLocalHoldRequestIdv2))
			.flatMap(localRequest -> ApplicationServices.convertToIll(illLocationId, localRequest.getLocalId()))
			.flatMap(listOfILLRequests -> getILLRequestId(patronLocalId, title))
			.flatMap(illRequestId -> ApplicationServices.transferRequest(illLocationId, illRequestId))
			.map(this::extractNeededInfo);
	}

	private LocalRequest extractNeededInfo(ApplicationServicesClient.ILLRequestInfo illRequestInfo) {
		log.info("extractNeededInfo for {}",illRequestInfo);

		return LocalRequest.builder()
				.localId(illRequestInfo.getIllRequestID() != null
					? illRequestInfo.getIllRequestID().toString()
					: "")
				.localStatus( String.valueOf(illRequestInfo.getIllRequestID()) )
				.requestedItemId( String.valueOf(illRequestInfo.getItemRecordID()) )
				.requestedItemBarcode( illRequestInfo.getItemBarcode() )
				.build();
	}
	
	/**
	 * Waits for a fixed delay and retries on failure.
	 * @param patronLocalId
	 * @return 
	 */
	@Retryable(attempts = "2", delay = "2s")
	protected <T> Mono<T> delayAndRetryTransformer ( Mono<T> targetFunction ) {
		return Mono.delay(Duration.ofSeconds(2))
			.then(targetFunction);
	}

	private Mono<Integer> getILLRequestId(String patronLocalId, String title) {
		
		return Mono.just(patronLocalId)
			.flatMap(ApplicationServices::getIllRequest)
			.transform(this::delayAndRetryTransformer)
			.doOnNext(entries -> log.debug("Got Polaris Holds: {}", entries))
			.flatMapMany(Flux::fromIterable)
			.filter(illRequest -> Objects.equals(illRequest.getTitle(), title))
			.next()
			.map(ApplicationServicesClient.ILLRequest::getIllRequestID)
			// We should retrieve the item record for the selected hold and store the barcode here
			.switchIfEmpty(Mono.error(new HoldRequestException("Error occurred when getting ILL Hold - filtering by title didn't match any request")))
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when getting Hold: {}", error.getMessage());
				return Mono.error(new HoldRequestException("NullPointerException occurred when getting ILL Hold"));
			});

	}

	public Mono<LocalRequest> getLocalHoldRequestIdv2(String patronId, String title, String note, String activationDate) {
		log.debug("getLocalHoldRequestIdv2({}, {}, {}, {})", patronId, title, note, activationDate);
		
		return Mono.just(patronId)
			.flatMap(ApplicationServices::listPatronLocalHolds)
			.transform(this::delayAndRetryTransformer)
			.doOnNext(entries -> log.debug("Got Polaris Holds: {}", entries))
			.flatMapMany(Flux::fromIterable)
			.filter(holds -> shouldIncludeHold(holds, title, note, activationDate))
			.collectList()
			.flatMap(this::chooseHold)
			// We should retrieve the item record for the selected hold and store the barcode here
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when getting Hold: {}", error.getMessage());
				return Mono.error(new HoldRequestException("Error occurred when getting Hold"));
			});
	}

	private Boolean shouldIncludeHold(ApplicationServicesClient.SysHoldRequest sysHoldRequest,
		String title, String note, String activationDate) {

		final var zonedDateTime = ZonedDateTime.parse(sysHoldRequest.getActivationDate());
		final var formattedDate = zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		if (Objects.equals(sysHoldRequest.getPacDisplayNotes(), note)) {
			log.info("hold matched on getPacDisplayNotes.");
			log.info("known title: {}=={}, " +
					"known activation date: {}=={}",
				title, sysHoldRequest.getTitle(),
				activationDate, formattedDate);

			return TRUE;
		}

		return FALSE;
	}

	/**
	 * Borrower requests use a real pickup location, supplier requests will usually pass through
	 * the agency code so we have a rough idea where the item is going
	 */
	private Mono<LocalRequest> placeHoldRequest(
		PlaceHoldRequestParameters parameters, boolean isVirtualItem) {

		log.info("placeHoldRequest {} {}", parameters, isVirtualItem);

		// If this is a borrowing agency then we are placing a hold on a virtual item. The item should have been created
		// with a home location of the declared ILL location. The pickup location should be the pickup location specified
		// by the borrower.

		// if this is a supplying agency then we will be using the ILL location as the pickup location for an item that
		// already exists. The vpatron should have been created at the ILL location.

		return getBibWithItem(parameters)
			.map(tuple -> {
				final var bib = tuple.getT1();
				final var item = tuple.getT2();

				String pickupLocation = getPickupLocation(parameters, isVirtualItem);

				log.info("Derived pickup location for hold isVirtualItem={} : {}", isVirtualItem, pickupLocation);

				return HoldRequestParameters.builder()
					.localPatronId(parameters.getLocalPatronId())
					.recordNumber(parameters.getLocalItemId())
					.title(bib.getBrowseTitle())
					.primaryMARCTOMID(bib.getPrimaryMARCTOMID())
					.pickupLocation(pickupLocation)
					.note(parameters.getNote())
					.dcbPatronRequestId(parameters.getPatronRequestId())
					.localItemLocationId(item.getAssignedBranchID())
					.bibliographicRecordID(bib.getBibliographicRecordID())
					.itemBarcode(item.getBarcode())
					.build();
			})
			.doOnNext(hr -> log.info("Attempt to place hold... {}", hr))
			.flatMap(ApplicationServices::createHoldRequestWorkflow)
			.doOnNext(__ -> log.info("Hold paced, attempting to get hold.. "))
			.doOnError(error -> log.error("Failed to place hold.. ", error))
			.flatMap(function(this::getLocalHoldRequestId));
	}

	public Mono<LocalRequest> getLocalHoldRequestId(
		String patronId, Integer bibId, String activationDate,
		String note, HoldRequestParameters parameters) {

		log.debug("getPatronHoldRequestId({}, {})", bibId, activationDate);

		final var fetchDelay = polarisConfig.getHoldFetchingDelay(5);
		final var maxFetchRetry = polarisConfig.getMaxHoldFetchingRetry(10);
		AtomicInteger retryCount = new AtomicInteger(0);

		return fetchAndProcessHolds(patronId, bibId, activationDate, note, parameters, fetchDelay)
			.retryWhen(Retry.max(maxFetchRetry + 1)
				.filter(throwable -> throwable instanceof Problem && retryCount.get() < maxFetchRetry)
				.doBeforeRetry(retrySignal -> log.debug("Fetch hold retry: {}", retryCount.incrementAndGet()))
			)
			.doOnSuccess(result -> log.debug("Fetch hold succeeded after {} retries", retryCount.get()))
			// We should retrieve the item record for the selected hold and store the barcode here
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when getting Hold: {}", error.getMessage());
				return Mono.error(new HoldRequestException("Error occurred when getting Hold"));
			});
	}

	public Mono<LocalRequest> fetchAndProcessHolds(String patronId, Integer bibId,
		String activationDate, String note, HoldRequestParameters parameters, Integer delay) {

		return Mono.just(patronId)
			.delayElement(Duration.ofSeconds(delay))
			.flatMap(ApplicationServices::listPatronLocalHolds)
			.doOnSuccess(resp -> log.info("Full log resp of getting hold after placing in {}: {}", getHostLmsCode(), resp))
			.doOnError(error -> log.error("Full log resp of getting hold after placing in {}", getHostLmsCode(), error))
			.doOnNext(logLocalHolds())
			.flatMap(holds -> processHolds(holds, bibId, activationDate, note, patronId, parameters));
	}

	private Mono<LocalRequest> processHolds(
		List<ApplicationServicesClient.SysHoldRequest> holds, Integer bibId,
		String activationDate, String note, String patronId,
		HoldRequestParameters parameters) {

		if (holds.isEmpty()) {
			return raiseError(Problem.builder()
				.withTitle("No holds to process for local patron id: " + patronId)
				.withDetail("Match attempted : bibId %s, activationDate %s, note %s".formatted(bibId, activationDate, note))
				.with("returned-list", holds)
				.with("hold-request-sent", parameters)
				.build());
		}

		final var holdCount = holds.size();

		return Flux.fromIterable(holds)
			.filter(hold -> shouldIncludeHold(hold, bibId, note, activationDate, holdCount))
			.collectList()
			.flatMap(this::chooseHold)
			.switchIfEmpty(raiseError(Problem.builder()
				.withTitle("Could not identify hold for local patron id: " + patronId)
				.withDetail("Match attempted : bibId %s, activationDate %s, note %s".formatted(bibId, activationDate, note))
				.with("holds-returned-count", holdCount)
				.with("returned-list", holds)
				.with("hold-request-sent", parameters)
				.build())
			);
	}

	private static Consumer<List<ApplicationServicesClient.SysHoldRequest>> logLocalHolds() {
		return entries -> log.debug("Retrieved {} local holds: {}", entries.size(), entries);
	}

	private Boolean shouldIncludeHold(ApplicationServicesClient.SysHoldRequest sysHoldRequest,
		Integer bibId, String note, String activationDate, Integer holdCount) {

		if (Objects.equals(sysHoldRequest.getBibliographicRecordID(), bibId) &&
			isEqualDisplayNoteIfPresent(sysHoldRequest, note)) {

			log.info("Hold found by bibId and note.");

			return TRUE;
		}

		else if (Objects.equals(sysHoldRequest.getBibliographicRecordID(), bibId) &&
			isEqualActivationDateIfPresent(sysHoldRequest, activationDate)) {

			log.info("Hold found by bibId and activationDate.");

			return TRUE;
		}

		else if (Objects.equals(sysHoldRequest.getBibliographicRecordID(), bibId) &&
			holdCount == 1) {

			log.info("Only hold found by bibId.");

			return TRUE;
		}

		return FALSE;
	}

	private static boolean isEqualActivationDateIfPresent(
		ApplicationServicesClient.SysHoldRequest sysHoldRequest, String activationDate) {

		if (sysHoldRequest.getActivationDate() != null && activationDate != null) {
			return Objects.equals(activationDate, sysHoldRequest.getActivationDate());
		}

		return FALSE;
	}

	private static boolean isEqualDisplayNoteIfPresent(
		ApplicationServicesClient.SysHoldRequest sysHoldRequest, String note) {

		final var returnedDisplayNote = notNullDisplayNote(sysHoldRequest);

		if (returnedDisplayNote != null && note != null) {
			final var tnoNote = extractTno(note);
			final var tnoReturned = extractTno(returnedDisplayNote);
			return Objects.equals(tnoReturned, tnoNote);
		}

		return TRUE;
	}

	private static String notNullDisplayNote(ApplicationServicesClient.SysHoldRequest sysHoldRequest) {
		return sysHoldRequest.getPacDisplayNotes() != null ? sysHoldRequest.getPacDisplayNotes() : null;
	}

	private static String extractTno(String str) {
		Pattern pattern = Pattern.compile("tno=[^\\s]+");
		Matcher matcher = pattern.matcher(str);
		if (matcher.find()) {
			return matcher.group();
		}
		return null;
	}

	private Mono<LocalRequest> chooseHold(List<ApplicationServicesClient.SysHoldRequest> filteredHolds) {
		log.debug("chooseHold({})", filteredHolds);

		if (filteredHolds.size() == 1) {

			final var sph = filteredHolds.get(0);

			final var extractedId = sph.getSysHoldRequestID();

			return getPlaceHoldRequestData(extractedId);

		} else if (filteredHolds.size() > 1) {
			throw new HoldRequestException("Multiple hold requests found: " + filteredHolds);
		} else {
			return Mono.empty();
		}
	}

	private String getPickupLocation(PlaceHoldRequestParameters parameters, boolean isVirtualItem) {
		// Different systems will use different pickup locations - we default to passing through
		// parameters.getPickupLocationCode

		// This is the code passed through from the users selected pickup location
		String pickup_location = parameters.getPickupLocationCode();

		// However - polaris as a pickup location actually needs to use the local ID of the pickup location
		// So if we have a specific local ID, pass that down the chain instead.
		if ( isVirtualItem && ( parameters.getPickupLocation() != null ) ) {
			if ( parameters.getPickupLocation().getLocalId() != null )
				log.debug("Overriding pickup location code with ID from selected record");
				pickup_location = parameters.getPickupLocation().getLocalId();
		}

		// supplier requests need the pickup location to be set as ILL
		if (isVirtualItem == FALSE) {
			pickup_location = String.valueOf(polarisConfig.getIllLocationId());
			if (pickup_location == null) {
				throw new IllegalArgumentException("Please add the config value 'ill-location-id' for polaris.");
			}
			return pickup_location;
		}

		return pickup_location;
	}

	private Mono<Tuple2<ApplicationServicesClient.BibliographicRecord, ApplicationServicesClient.ItemRecordFull>> getBibWithItem(
		PlaceHoldRequestParameters parameters) {
		return getBibIdFromItemId(parameters.getLocalItemId())
			.flatMap(this::getBib)
			.zipWith(ApplicationServices.itemrecords(parameters.getLocalItemId(), FALSE));
	}

	@Override
	public Mono<HostLmsRequest> getRequest(HostLmsRequest request) {

		final var localRequestId = getValueOrNull(request, HostLmsRequest::getLocalId);

		log.info("getRequest({})", localRequestId);

		return parseLocalRequestId(localRequestId)
			.flatMap(ApplicationServices::getLocalHoldRequest)
			.map(hold -> HostLmsRequest.builder()
				.localId(localRequestId)
				.status(checkHoldStatus(hold.getSysHoldStatus()))
				.rawStatus(hold.getSysHoldStatus())
				.requestedItemId(getValueOrNull(hold, LibraryHold::getItemRecordID, Object::toString))
				.requestedItemBarcode(getValueOrNull(hold, LibraryHold::getItemBarcode))
				.build());
	}

	private Mono<Integer> parseLocalRequestId(String localRequestId) {
		try {
			int parsedLocalRequestId = Integer.parseInt(localRequestId);
			return Mono.just(parsedLocalRequestId);
		} catch (NumberFormatException e) {
			return Mono.error(new NumberFormatException("Cannot convert localRequestId: " + localRequestId + " to an Integer."));
		} catch (NullPointerException e) {
			return Mono.error(new NullPointerException("Cannot use null localRequestId to fetch local request."));
		}
	}

	/**
	 * From <a href="https://qa-polaris.polarislibrary.com/polaris.applicationservices/help/sysholdstatuses/get_syshold_statuses">statuses</a>
	 */
	private String checkHoldStatus(String status) {
		log.debug("Checking hold status: {}", status);
		return switch (status) {
			case "Cancelled" -> HostLmsRequest.HOLD_CANCELLED;
			case "Pending", "Active",
				// Edge case that the item has been put in transit by staff
				// before DCB had a chance to confirm the supplier request
				"Shipped" -> HostLmsRequest.HOLD_CONFIRMED;
			case "Held" -> HostLmsRequest.HOLD_READY;
			case "Missing" -> HostLmsRequest.HOLD_MISSING;
			default -> status;
		};
	}

	@Override
	public Mono<String> createBib(Bib bib) {
		if (ILL_BORROWING_FLOW.equals(borrowerlendingFlow())) {
			log.warn(ILL_BORROWING_FLOW + " SET FOR POLARIS, CREATE BIB WILL RETURN PLACEHOLDER");
			return Mono.just("ILL_REQUEST_BIB_ID_PLACEHOLDER");
		}

		return ApplicationServices.createBibliographicRecord(bib).map(String::valueOf);
	}

	@Override
	public Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters) {

		final var localId = parameters.getPatronId();
		final var RequestID = parameters.getLocalRequestId();
		final var wsid = polarisConfig.getServicesWorkstationId();
		final var userid = polarisConfig.getLogonUserId();

		return ApplicationServices.getPatronBarcode(localId)
				.flatMap(barcode -> PAPIService.holdRequestCancel(barcode, RequestID, wsid, userid));
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand createItemCommand) {
		log.info("createItem({})",createItemCommand);

		if (ILL_BORROWING_FLOW.equals(borrowerlendingFlow())) {
			log.warn(ILL_BORROWING_FLOW + " SET FOR POLARIS, CREATE ITEM WILL RETURN PLACEHOLDER");
			return Mono.just(HostLmsItem.builder().localId("ILL_REQUEST_ITEM_ID_PLACEHOLDER").build());
		}

		return ApplicationServices.addItemRecord(createItemCommand)
			.doOnSuccess(r -> log.info("Got create item response from Polaris: {}",r))
			.map(itemCreateResponse -> {
				if (itemCreateResponse.getAnswerExtension() == null) {
					final var messages = itemCreateResponse.getInformationMessages() != null
						? itemCreateResponse.getInformationMessages().toString()
						: "NO DETAILS";

					throw new RuntimeException("Missing answer" + messages);
				}
				return HostLmsItem.builder()
					.localId(String.valueOf(itemCreateResponse.getAnswerExtension().getAnswerData().getItemRecord().getItemRecordID()))
					.status(String.valueOf(itemCreateResponse.getAnswerExtension().getAnswerData().getItemRecord().getItemStatusID()))
					.rawStatus(itemCreateResponse.getAnswerExtension().getAnswerData().getItemRecord().getItemStatusDescription())
					.build();
			})
			.onErrorMap(error -> {

				if (error instanceof Problem) return error;

				log.error("Error attempting to create item {} : {}", createItemCommand, error.getMessage());
				return Problem.builder()
					.withType(ERR0211)
					.withTitle("Unable to create virtual item at polaris - pr=" + createItemCommand.getPatronRequestId()
						+ " cit=" + createItemCommand.getCanonicalItemType())
					.withDetail(error.getMessage())
					.with("createItemCommand", createItemCommand)
					.build();
			});
	}

	@Override
	public Mono<List<Item>> getItems(BibRecord bib) {
		final var localBibId = bib.getSourceRecordId();

		// @see: https://documentation.iii.com/polaris/PAPI/7.1/PAPIService/Synch_ItemsByBibIDGet.htm
		return PAPIService.synch_ItemGetByBibID(localBibId)
			.flatMapMany(Flux::fromIterable)
			.flatMap(this::fetchFullItemStatus)
			.flatMap(this::setMaterialTypeCode)
			.flatMap(mapItemWithRuleset(localBibId))
			.flatMap(this::enrichWithCombinedNumberOfHoldsOnItem)
			.collectList();
	}

	private Function<PAPIClient.ItemGetRow, Publisher<Item>> mapItemWithRuleset(String localBibId) {
		return result -> getLmsItemSuppressionRuleset()
			.flatMap(objectRuleset -> itemMapper.mapItemGetRowToItem(result, lms.getCode(), localBibId, objectRuleset, polarisConfig));
	}

	private Mono<DataHostLms> refetchLms() {
		
		return Mono.fromSupplier( lms::getId )
			.flatMap( hostLmsService::findById )
			.cacheInvalidateWhen( cacheInvalidator::getInvalidator );
	}

	// bib suppression not supported in Polaris
//	private final Mono<ObjectRuleset> _lmsBibSuppressionRuleset = null;

	private Mono<Optional<ObjectRuleset>> _lmsItemSuppressionRuleset = null;
	private synchronized Mono<Optional<ObjectRuleset>> getLmsItemSuppressionRuleset() {

		if (_lmsItemSuppressionRuleset == null) {

			_lmsItemSuppressionRuleset = refetchLms()
				.mapNotNull( HostLms::getItemSuppressionRulesetName )
				.flatMap( name -> objectRuleService.findByName(name)
					.doOnSuccess(val -> {
						if (val == null) {
							log.warn("Host LMS [{}] specified using ruleset [{}] for item suppression, but no ruleset with that name could be found", lms.getCode(), name);
							return;
						}

						log.debug("Found item suppression ruleset [{}] for Host LMS [{}]", name, lms.getCode());
					}))
				.singleOptional()
				.cacheInvalidateWhen( cacheInvalidator::getInvalidator );
		}
		return  _lmsItemSuppressionRuleset;
	}

	@Override
	public Mono<HostLmsItem> getItem(HostLmsItem hostLmsItem) {

		final var localItemId = hostLmsItem.getLocalId();

		return parseLocalItemId(localItemId)
			.flatMap(id -> ApplicationServices.itemrecords(id,TRUE))
			.doOnSuccess(itemRecordFull -> log.info("Got item: {}", itemRecordFull))
			.map(item -> validate(localItemId, item))
			.flatMap(this::collectItemStatusName)
			.map(itemRecord -> {
				final var hostLmsStatus = mapItemStatus(POLARIS_TO_HOST_LMS, itemRecord.getItemStatusName());

				final var renewalCount = Optional.of(itemRecord)
					.map(ApplicationServicesClient.ItemRecordFull::getCirculationData)
					.map(ApplicationServicesClient.CirculationData::getRenewalCount)
					.orElse(0);

				return HostLmsItem.builder()
					.localId(String.valueOf(itemRecord.getItemRecordID()))
					.status(hostLmsStatus)
					.rawStatus(itemRecord.getItemStatusName())
					// CH: Note that itemStatusName no longer appears to exist in Polaris API responses. No sign in docs of when this changed.
					// As such we will need to re-work the above.
					.barcode(itemRecord.getBarcode())
					.renewalCount(renewalCount)
					.renewable(itemRecord.getBibInfo().getCanItemBeRenewed()) // Seems to be false until the item is checked out.
					.build();
			})
			.flatMap( this::enrichWithCombinedNumberOfHoldsOnItem )
			.defaultIfEmpty(HostLmsItem.builder()
				.localId(localItemId)
				.status("MISSING")
				.build());
	}


	private Mono<Item> enrichWithCombinedNumberOfHoldsOnItem(Item item) {
		String localId = item.getLocalId();
		Integer currentHoldCount = item.getHoldCount();

		return enrichHoldCount(localId, currentHoldCount)
			.map(updatedHoldCount -> {
				item.setHoldCount(updatedHoldCount);
				return item;
			});
	}

	private Mono<HostLmsItem> enrichWithCombinedNumberOfHoldsOnItem(HostLmsItem item) {
		String localId = item.getLocalId();
		Integer currentHoldCount = item.getHoldCount();

		return enrichHoldCount(localId, currentHoldCount)
			.map(updatedHoldCount -> {
				item.setHoldCount(updatedHoldCount);
				return item;
			});
	}

	/**
	 // Make calls to get any holds on the item, or on the title
	 */
	private Mono<Integer> enrichHoldCount(String localId, Integer currentHoldCount) {

		// prevent NPEs
		final int currentCount = currentHoldCount != null ? currentHoldCount : 0;
		if (localId == null) return Mono.just(currentCount);

		// Convert localId to Integer for the reservation request
		Integer itemId = Integer.valueOf(localId);

		return ApplicationServices.getReservationsForItem(itemId)
			.map(response -> {
				// If there's a valid response with reservation data
				if (response != null && response.getReservations() != null) {
					// Count the number of reservations for this item
					int externalHoldCount = response.getReservations().size();
					return externalHoldCount;
				}
				// Fall back to the original count if no reservation data is available
				return currentCount;
			})
			// Handle getReservationsForItem returning empty
			.defaultIfEmpty(currentCount);
	}

//	private Mono<Integer> enrichHoldCount(String localId, Integer currentHoldCount) {
//
//		// prevent NPEs
//		final int currentCount = currentHoldCount != null ? currentHoldCount : 0;
//		if (localId == null) return Mono.just(currentCount);
//
//		// Convert localId to Integer for the reservation request
//		Integer itemId = Integer.valueOf(localId);
//
//		return ApplicationServices.getReservationsForItem(itemId)
//			.map(response -> {
//				// If there's a valid response with reservation data
//				if (response != null && response.getReservations() != null) {
//					// Count the number of reservations for this item
//					int externalHoldCount = response.getReservations().size();
//					return externalHoldCount;
//
////					if (externalHoldCount > 0)
////					{
////						return externalHoldCount;
////
////					}
////					else {
////						// There is a situation where the reservation data is not present, but the hold count is not zero
////						// In this situation, we should use the total count as a fallback
////						if (response.getTotalCount() != null && response.getTotalCount() > 0)
////						{
////							return response.getTotalCount();
////
////						}
////						else
////						{
////							// If the total count is not present or is zero, fall back to original count
////							return currentCount;
////						}
////					}
//				}
//				// Fall back to the original count if no reservation data or total count is available
//				return currentCount;
//			})
//			// Handle getReservationsForItem returning empty
//			.defaultIfEmpty(currentCount);
//	}

	private Mono<String> parseLocalItemId(String localItemId) {
		if (localItemId == null) {
			return Mono.error(new NullPointerException("Cannot use null localItemId to fetch local item."));
		}
		return Mono.just(localItemId);
	}

	private ApplicationServicesClient.ItemRecordFull validate(
		String knownId, ApplicationServicesClient.ItemRecordFull item) {

		final var fetchedId = String.valueOf(item.getItemRecordID());

		if (Objects.equals(knownId, fetchedId) &&
		item.getItemStatusDescription() != null &&
		item.getBarcode() != null) {

			return item;
		}

		log.error("Unexpected item record fetched. Known id: {} fetched record: {}", knownId, item);
		throw new IllegalArgumentException("Fetched record wasn't validated.");
	}

	private Mono<ApplicationServicesClient.ItemRecordFull> collectItemStatusName(
		ApplicationServicesClient.ItemRecordFull itemRecord) {

		final var description = itemRecord.getItemStatusDescription();

		return fetchItemStatusObjectBy(SearchType.DESCRIPTION, description)
			.map(itemStatus -> {

				final var name = itemStatus.getName();
				itemRecord.setItemStatusName(name);

				return itemRecord;
			});
	}

	@Override
	public Mono<String> updateItemStatus(HostLmsItem hostLmsItem, CanonicalItemState crs) {
		log.warn("Attempting to update an item status.");

		final var itemId = getValueOrNull(hostLmsItem, HostLmsItem::getLocalId);
		
		return switch (crs) {
			case AVAILABLE -> updateItemToAvailable(itemId).thenReturn("OK");
			case TRANSIT -> updateItemToPickupTransit(itemId).thenReturn("OK");
			default -> Mono.just("OK").doOnSuccess(ok ->
				log.error("CanonicalItemState: '{}' cannot be updated.", crs));
		};
	}

	private Mono<Void> updateItemToAvailable(String itemId) {
		return fetchItemStatusObjectBy(SearchType.NAME, AVAILABLE)
			.map(PolarisItemStatus::getItemStatusID)
			.flatMap(statusId -> updateItem(itemId, statusId));
	}

	private Mono<Void> updateItemToPickupTransit(String itemId) {
		return ApplicationServices.checkIn(itemId, polarisConfig.getIllLocationId()).then();
	}

	private Mono<Void> updateItem(String itemId, Integer toStatus) {

		return ApplicationServices.itemrecords(itemId,FALSE)
			.flatMap(item -> ApplicationServices.updateItemRecordStatus(itemId, item.getItemStatusID(), toStatus));
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		final var barcodeListAsString = getValueOrNull(patron,
			org.olf.dcb.core.model.Patron::determineHomeIdentityBarcode);

		final var firstBarcodeInList = parseList(barcodeListAsString).get(0);
		// note: if a patron isn't found a empty mono will need to be returned
		// to then create the patron
		return PAPIService.patronSearch(firstBarcodeInList)
			.map(PAPIClient.PatronSearchRow::getPatronID)
			.flatMap(this::foundVirtualPatron);	}

	private Mono<Patron> foundVirtualPatron(Integer patronId) {
		log.info("Found virtual patron with local id: {}", patronId);

		return ApplicationServices.handlePatronBlock(patronId)
			.map(String::valueOf)
			.flatMap(ApplicationServices::getVirtualPatronAndCheckExpiry)	// Check expiry and update if needed
			.flatMap(this::enrichWithCanonicalPatronType)
			.switchIfEmpty(Mono.defer(() -> Mono.error(patronNotFound(String.valueOf(patronId), getHostLmsCode()))));
	}

	@Override
	public Mono<String> createPatron(Patron patron) {
		log.info("createPatron({}) at {}", patron, lms);

		return collectDefaultsForCreatePatron(patron)
			.flatMap(PAPIService::patronRegistrationCreate)
			.doOnSuccess(response -> log.debug("Successful result creating patron: {}", response))
			.flatMap(result -> validateCreatePatronResult(result, patron))
			.doOnError(error -> log.error("Error trying to create patron: ", error));
	}

	private Mono<Patron> collectDefaultsForCreatePatron(Patron patron) {
		return fetchItemLocation(patron)
			.flatMap(this::fetchPatronDefaultsByOrg);
	}

	private Mono<Patron> fetchPatronDefaultsByOrg(Patron patron) {
		return ApplicationServices.patrondefaults(polarisConfig.getIllLocationId())
			.onErrorResume(error -> {
				log.error(error.getMessage() != null ? error.getMessage() : "Unable to get patron defaults.");

				log.info("fetchPatronDefaultsByOrg using empty strings as fallback");
				return Mono.just(ApplicationServicesClient.PatronDefaults.builder().city("").state("").postalCode("").build());
			})
			.map(chain -> {
				patron.setCity(chain.getCity());
				patron.setState(chain.getState());
				patron.setPostalCode(chain.getPostalCode());
				return patron;
			});
	}

	private Mono<Patron> fetchItemLocation(Patron patron) {
		return ApplicationServices.itemrecords(patron.getLocalItemId(),FALSE)
			.map(item -> patron.setLocalItemLocationId(item.getAssignedBranchID()));
	}

	private Mono<String> validateCreatePatronResult(
		PAPIClient.PatronRegistrationCreateResult result, Patron patron) {

		final var errorCode = result.getPapiErrorCode();

		// Perform a test on result.papiErrorCode
		if (errorCode != 0) {
			final var errorMessage = result.getErrorMessage();

			return Mono.error(
				Problem.builder()
					.withType(ERR0211)
					.withTitle("Unable to create virtual patron at polaris - error code: %d"
						.formatted(errorCode))
					.withDetail(errorMessage)
					.with("patron", patron)
					.with("errorCode", errorCode)
					.with("errorMessage", errorMessage)
					.build());
		}

		// we expect a block to be added when creating a virtual patron
		// check and remove it if present
		return ApplicationServices.handlePatronBlock(result.getPatronID()).map(String::valueOf);
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		// may need to get patron or get patron defaults in order to retrieve data for updating.
		return ApplicationServices.getPatronBarcode(localId)
			.flatMap(barcode -> PAPIService.patronRegistrationUpdate(barcode, patronType))
			.flatMap(barcode -> getPatronByLocalId(localId));
	}

	@Override
	public Mono<String> findLocalPatronType(String canonicalPatronType) {
		return referenceValueMappingService.findMapping("patronType", "DCB",
				canonicalPatronType, getHostLmsCode())
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.error(new NoPatronTypeMappingFoundException(
				String.format("Patron type mapping missing. " +
						"Details: fromContext: %s, fromCategory: %s, fromValue: %s, toContext: %s, toCategory: %s, toValue: %s",
					"DCB", "patronType", canonicalPatronType, getHostLmsCode(), "patronType", null)
			)));
	}

	@Override
	public Mono<String> findCanonicalPatronType(String localPatronType, String localId) {
		return numericPatronTypeMapper.mapLocalPatronTypeToCanonical(
			getHostLmsCode(), localPatronType, localId);
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {

		if (safeParseInteger(localPatronId) == null) {
			log.error("GUARD CLAUSE : localPatronId '{}' cannot be parsed as integer", localPatronId);

			return Mono.error(patronNotFound(localPatronId, getHostLmsCode()));
		}

		return ApplicationServices.getPatron(localPatronId)
			.flatMap(this::enrichWithCanonicalPatronType)
			.zipWhen(this::getPatronCirculationBlocks, PolarisLmsClient::isBlocked)
			.switchIfEmpty(Mono.defer(() -> Mono.error(patronNotFound(localPatronId, getHostLmsCode()))));
	}

	public Integer safeParseInteger(String str) {
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	public Mono<Patron> getPatronByIdentifier(String identifier) {
			return getPatronByLocalId(identifier)
				.switchIfEmpty(Mono.error(patronNotFound(identifier, getHostLmsCode())));
	}

	private Mono<Patron> enrichWithCanonicalPatronType(Patron patron) {
		return numericPatronTypeMapper.mapLocalPatronTypeToCanonical(getHostLmsCode(),
				patron.getLocalPatronType(), patron.getFirstLocalId())
			.map(patron::setCanonicalPatronType)
			.defaultIfEmpty(patron);
	}

	private Mono<PatronCirculationBlocksResult> getPatronCirculationBlocks(Patron patron) {
		log.info("getPatronCirculationBlocks: {}", patron);

		final var barcode = getValueOrNull(patron, Patron::getFirstBarcode);

		return PAPIService.getPatronCirculationBlocks(barcode);
	}

	private static Patron isBlocked(Patron patron, PatronCirculationBlocksResult blocks) {
		final var canCirculate = getValue(blocks,
			PatronCirculationBlocksResult::getCanPatronCirculate, true);

		return patron.setIsBlocked(!canCirculate);
	}

	@Override
	public Mono<Patron> getPatronByUsername(String username) {

		// note: the auth controller is passing a patron barcode here
		log.info("getPatronByUsername using barcode: {}", username);
		final var barcode = username;

		return ApplicationServices.getPatronIdByIdentifier(barcode, "barcode")
			.doOnSuccess(id -> log.info("getPatronByUsername found patron id: {}", id))
			.flatMap(this::getPatronByLocalId)
			.switchIfEmpty(Mono.defer(() -> Mono.error(patronNotFound(username, getHostLmsCode()))));
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret) {
		return switch (authProfile) {
			case "BASIC/BARCODE+PIN" -> PAPIService.patronValidate(patronPrinciple, secret);
			case "BASIC/BARCODE+PASSWORD" -> PAPIService.patronValidate(patronPrinciple, secret);
			default -> Mono.empty();
		};
	}

	@Override
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkout) {

		final var itemId = getValueOrNull(checkout, CheckoutItemCommand::getItemId);
		final var localRequestId = getValueOrNull(checkout, CheckoutItemCommand::getLocalRequestId);
		final var patronId = getValueOrNull(checkout, CheckoutItemCommand::getPatronId);

		if (itemId == null) {
			return Mono.error(new MissingParameterException("itemId"));
		}

		if (localRequestId == null) {
			log.warn("checkOutItemToPatron: localRequestId is null");
		}

		if (patronId == null) {
			return Mono.error(new MissingParameterException("patronId"));
		}

		final var patronBarcode = getValueOrNull(checkout, CheckoutItemCommand::getPatronBarcode);
		final var hostLmsItem = HostLmsItem.builder().localId(itemId).build();

		return updateItemStatus(hostLmsItem, CanonicalItemState.AVAILABLE)
			.doOnNext(__ -> log.info("checkOutItemToPatron({}, {}, {})", itemId, patronBarcode, localRequestId))
			.then(Mono.zip(
				ApplicationServices.getItemBarcode(itemId),
				ApplicationServices.getPatronBarcode(patronId)
			))
			.flatMap(tuple -> PAPIService.itemCheckoutPost(tuple.getT1(), tuple.getT2()))
			.map(itemCheckoutResult -> "OK");
	}

	private Mono<LocalRequest> getPlaceHoldRequestData(Integer holdRequestId) {
		log.info("Get hold request data for {}", holdRequestId);

		return ApplicationServices.getLocalHoldRequest(holdRequestId)
			.doOnSuccess(hold -> {
				// TODO: add extra check on notes
				final var nonPublicNotes = hold.getNonPublicNotes();
				final var staffDisplayNotes = hold.getStaffDisplayNotes();
				log.info("nonPublicNotes: {}, staffDisplayNotes: {}", nonPublicNotes, staffDisplayNotes);
			})
			.doOnSuccess(hold -> log.debug("Received hold from Host LMS \"%s\": %s"
				.formatted(getHostLmsCode(), hold)))
			.map(response -> LocalRequest.builder()
				.localId(holdRequestId != null
					? holdRequestId.toString()
					: "")
				.localStatus( getLocalStatus(response) )
				.rawLocalStatus(getValue(response, LibraryHold::getSysHoldStatus, Object::toString, ""))
				.requestedItemId(getValueOrNull(response, LibraryHold::getItemRecordID, Object::toString))
				.requestedItemBarcode(getValueOrNull(response, LibraryHold::getItemBarcode))
				.build());
	}

	private String getLocalStatus(LibraryHold response) {
		var value = getValue(response, LibraryHold::getSysHoldStatus, Object::toString, "");
		return checkHoldStatus(value);
	}

	private Mono<ApplicationServicesClient.BibliographicRecord> getBib(String localBibId) {
		return ApplicationServices.getBibliographicRecordByID(localBibId);
	}

	private Mono<String> getBibIdFromItemId(String recordNumber) {
		return ApplicationServices.itemrecords(recordNumber,FALSE)
			.map(record -> record.getBibInfo().getBibliographicRecordID())
			.map(String::valueOf);
	}

	private Mono<PAPIClient.ItemGetRow> setMaterialTypeCode(PAPIClient.ItemGetRow itemGetRow) {
		return (materialTypes.isEmpty() ? ApplicationServices.listMaterialTypes().doOnNext(materialTypes::addAll)
			: Mono.just(materialTypes))
			.flatMapMany(Flux::fromIterable)
			.filter(materialType -> itemGetRow.getMaterialType().equals(materialType.getDescription()))
			.map(materialType -> String.valueOf(materialType.getMaterialTypeID()))
			.next()
			.doOnSuccess(itemGetRow::setMaterialTypeID)
			.thenReturn(itemGetRow);
	}

	private Mono<PAPIClient.ItemGetRow> fetchFullItemStatus(PAPIClient.ItemGetRow itemGetRow) {

		final var description = itemGetRow.getCircStatus();

		return fetchItemStatusObjectBy(SearchType.DESCRIPTION, description)
			.map(status -> {
				itemGetRow.setCircStatusID(status.getItemStatusID());
				itemGetRow.setCircStatusName(status.getName());
				itemGetRow.setCircStatusBanner(status.getBannerText());
				return itemGetRow;
			});
	}
	
	private Mono<PolarisItemStatus> fetchItemStatusObjectBy(SearchType type, String value) {

		return fetchItemStatusesFromApi()
			.flatMap(list -> switch (type) {
				case DESCRIPTION -> matchByDescription(list, value);
				case NAME -> matchByName(list, value);
			});
	}

	enum SearchType { DESCRIPTION, NAME }

	private Mono<PolarisItemStatus> matchByName(List<PolarisItemStatus> list, String name) {
		return Flux.fromIterable(list)
			.filter(status -> name.equals(status.getName()))
			.next() // Take the first matching item status
			.switchIfEmpty(Mono.error(new IllegalArgumentException("No item status found with name: " + name)));
	}

	private Mono<PolarisItemStatus> matchByDescription(List<PolarisItemStatus> list, String description) {
		return Flux.fromIterable(list)
			.filter(status -> description.equals(status.getDescription()))
			.next() // Take the first matching item status
			.switchIfEmpty(Mono.error(new IllegalArgumentException("No item status found with description: " + description)));
	}

	private Mono<List<PolarisItemStatus>> fetchItemStatusesFromApi() {

		log.info("Fetching item statuses...");

		return statuses.isEmpty()
			? ApplicationServices.listItemStatuses().doOnNext(statuses::addAll)
			: Mono.just(statuses);
	}

	/**
	 * Make HTTP request to a Polaris system
	 *
	 * @param request Request to send
	 * @return Deserialized response body or error
	 * @param <T> Type to deserialize the response to
	 */
	<T> Mono<HttpResponse<T>> exchange(MutableHttpRequest<?> request, Class<T> returnClass,
		Boolean useGenericHttpClientResponseExceptionHandler) {
		return Mono.from(client.exchange(request, returnClass))
			.doOnError(logRequestAndResponseDetails(request))
			.onErrorResume(error -> {

				// we want to automatically handle HttpClientResponseExceptions
				if (error instanceof HttpClientResponseException && useGenericHttpClientResponseExceptionHandler) {
					return raiseError(unexpectedResponseProblem((HttpClientResponseException) error, request, getHostLmsCode()));
				}

				// we want to manually handle HttpClientResponseExceptions
				else if (error instanceof HttpClientResponseException) { // useGenericHttpClientResponseExceptionHandler == false
					return raiseError(error);
				}

				// an error happened before we got a response
				return raiseError(unexpectedResponseProblem(error, request, getHostLmsCode()));
			});
	}

	/**
	 * Make HTTP request to a Polaris system with no extra error handling
	 *
	 * @param request Request to send
	 * @param responseBodyType Expected type of the response body
	 * @return Deserialized response body or error, that might have been transformed already by handler
	 * @param <T> Type to deserialize the response to
	 */
	<T> Mono<T> retrieve(MutableHttpRequest<?> request, Argument<T> responseBodyType) {
		return retrieve(request, responseBodyType, noExtraErrorHandling());
	}

	/**
	 * Make HTTP request to a Polaris system
	 *
	 * @param request Request to send
	 * @param responseBodyType Expected type of the response body
	 * @param errorHandlingTransformer method for handling errors after the response has been received
	 * @return Deserialized response body or error, that might have been transformed already by handler
	 * @param <T> Type to deserialize the response to
	 */
	<T> Mono<T> retrieve(MutableHttpRequest<?> request, Argument<T> responseBodyType,
		Function<Mono<T>, Mono<T>> errorHandlingTransformer) {

		return Mono.from(client.retrieve(request, responseBodyType))
			.doOnError(logRequestAndResponseDetails(request))
			// Additional request specific error handling
			.transform(errorHandlingTransformer)
			// This has to go after more specific error handling
			// as will convert any client response exception to a problem
			.doOnError(HttpClientResponseException.class, error -> log.error("Unexpected response from Host LMS: {}", getHostLmsCode(), error))
			.onErrorMap(HttpClientResponseException.class, responseException ->
				unexpectedResponseProblem(responseException, request, getHostLmsCode()))
			.onErrorResume(error -> {
				if (error instanceof Problem) {
					return Mono.error(error);
				}

				return raiseError(unexpectedResponseProblem(error, request, getHostLmsCode()));
			});
	}

	private static Consumer<Throwable> logRequestAndResponseDetails(MutableHttpRequest<?> request) {
		return error -> {
			try {
				log.error("""
						HTTP Request and Response Details:
						URL: {}
						Method: {}
						Headers: {}
						Body: {}
						Response: {}""",
					request.getUri(),
					request.getMethod(),
					request.getHeaders().asMap(),
					request.getBody().orElse(null),
					error.toString());
			} catch (Exception e) {
				log.error("Couldn't log error request and response details", e);
			}
		};
	}

	/**
	 * Utility method to specify that no specialised error handling will be needed for this request
	 *
	 * @return transformer that provides no additionally error handling
	 * @param <T> Type of response being handled
	 */
	static <T> Function<Mono<T>, Mono<T>> noExtraErrorHandling() {
		return Function.identity();
	}

	<T> Mono<MutableHttpRequest<?>> createRequest(HttpMethod method, String path) {
		log.info("{} {}", method, path);

		return Mono.just(UriBuilder.of(path).build())
			.map(this::defaultResolve)
			.map(resolvedUri -> HttpRequest.<T>create(method, resolvedUri.toString()).accept(APPLICATION_JSON));
	}

	<T> Mono<MutableHttpRequest<?>> createRequestWithOverrideURL(HttpMethod method, String path) {
		log.info("{} {}", method, path);

		return Mono.just(UriBuilder.of(path).build())
			.map(this::overrideResolve)
			.map(resolvedUri -> HttpRequest.<T>create(method, resolvedUri.toString()).accept(APPLICATION_JSON));
	}

	Boolean isApplicationServicesBaseUrlPresent() {
		return applicationServicesOverrideURL != null ? TRUE : FALSE;
	}

	URI defaultResolve(URI relativeURI) {
		return RelativeUriResolver.resolve(defaultBaseUrl, relativeURI);
	}

	URI overrideResolve(URI relativeURI) {
		return RelativeUriResolver.resolve(applicationServicesOverrideURL, relativeURI);
	}

	private UUID uuid5ForBibPagedRow(@NotNull final BibsPagedRow result) {
		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + result.getBibliographicRecordID();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	private UUID uuid5ForRawJson(@NotNull final BibsPagedRow result) {
		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":raw:" + result.getBibliographicRecordID();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public boolean isEnabled() {
		return MapUtils.getAsOptionalString(lms.getClientConfig(), "ingest").map(StringUtils::isTrue).orElse(TRUE);
	}

	@Override
	public @NonNull String getName() {
		return lms.getName();
	}

	@Override
	public HostLms getHostLms() { return lms; }

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return Collections.emptyList();
	}

	@Override
	public Mono<String> deleteItem(DeleteCommand deleteCommand) {

		final var id = getValueOrNull(deleteCommand, DeleteCommand::getItemId);

		// workflow POST delete
		// workflow PUT continue delete
		// workflow PUT don't delete bib if last item
		// ERROR PolarisWorkflowException
		return ApplicationServices.deleteItemRecord(id)
				.flatMap(workflowResponse -> {
					if (Objects.equals(workflowResponse.getWorkflowStatus(), ApplicationServicesClient.WorkflowResponse.CompletedSuccessfully)) {
						log.debug("Workflow success for item {}", id);
						// The workflow can complete without the item necessarily being deleted
						// Hence a success is not a definite indicator that the item definitely got deleted in Polaris
						// We need to verify that it has been included in the deleted list
						// https://qa-polaris.polarislibrary.com/Polaris.ApplicationServices/help/workflow/delete_item_record
						var answerData = Optional.ofNullable(workflowResponse.getAnswerExtension())
							.map(ApplicationServicesClient.AnswerExtension::getAnswerData)
							.orElse(null);

						// More of a theoretical scenario than anything else, really
						if (answerData == null) {
							log.warn("Workflow for item {} completed successfully (Status: 1) but returned no AnswerExtension data. Unable to confirm virtual item deletion.", id);
							return Mono.just("OK_WORKFLOW_NO_ANSWER");
						}

						// If our ID is in the deleted list, we're definitely all good
						if (Optional.ofNullable(answerData.getDeletedRecordIds()).orElse(Collections.emptyList()).contains(id)) {
							log.info("Successfully deleted item record {}", id);
							return Mono.just("OK");
						}

						// Apparently IDs can also be in the blocked list.
						// This is important as it would appear to be deleted, but it wouldn't actually have been deleted
						if (Optional.ofNullable(answerData.getBlockedRecordIds()).orElse(Collections.emptyList()).contains(id)) {
							log.warn("Workflow for item {} completed (Status: 1), but deletion was BLOCKED by Polaris.", id);
							return Mono.just("OK_WORKFLOW_BLOCKED");
						}
						// Weirdly it wasn't in either list
						log.warn("Polaris Workflow for item {} completed but ID was not present in the response", id);
						return Mono.just("OK_WORKFLOW_ID_MISSING");
					}
					else
					{
						log.warn("deleteItemRecord for item {} completed with non-success status: {}. Response: {}", id, workflowResponse.getWorkflowStatus(), workflowResponse);
						return Mono.just("OK"); // Appears there is a scenario where we don't get an immediate success response but we do still manage to delete the item
					}
			})
			.defaultIfEmpty("ERROR");// And if the response is empty, assume it failed
	}

	@Override
	public Mono<String> deleteBib(String id) {
		// workflow POST delete
		// workflow PUT continue delete
		// ERROR PolarisWorkflowException
		return ApplicationServices.deleteBibliographicRecord(id).thenReturn("OK").defaultIfEmpty("ERROR");
	}

  public Mono<String> deletePatron(String id) {
    log.info("Delete patron is not currently implemented");
    return Mono.empty();
  }

	@Override
	public String getDefaultControlIdNamespace() {
		return lms.getName();
	}

  // N.B.   public Mono<SourceRecordImportChunk> getChunk( Optional<JsonNode> checkpoint ) is now the main entry point for harvest v2 NOT this method
	@Override
	public Publisher<BibsPagedRow> getResources(Instant since, Publisher<String> terminator) {
		log.info("Fetching MARC JSON from Polaris for {}", lms.getName());

		Integer pageSize = polarisConfig.getPageSize();
		if (pageSize > 90) {
			log.info("Limiting POLARIS page size to 100");
			pageSize = 90;
		}

		return Flux.from( ingestHelper.pageAllResults(pageSize, terminator) )
			.filter(bibsPagedRow -> bibsPagedRow.getBibliographicRecordXML() != null)
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
		return convertToMarcRecord(resource.getBibliographicRecordXML());
	}

	@Override
	public RawSourceRepository getRawSourceRepository() {
		return rawSourceRepository;
	}

	@Override
	public RawSource resourceToRawSource(BibsPagedRow resource) {
		final var record = convertToMarcRecord(resource.getBibliographicRecordXML());
		final var rawJson = conversionService.convertRequired(record, JsonNode.class);

		@SuppressWarnings("unchecked")
		final Map<String, ?> rawJsonString = conversionService.convertRequired(rawJson, Map.class);

		return RawSource.builder()
			.id(uuid5ForRawJson(resource))
			.hostLmsId(lms.getId())
			.remoteId(String.valueOf(record.getId()))
			.json(rawJsonString)
			.build();
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

	Publisher<BibsPagedResult> getBibs(String date, Integer lastId, Integer nrecs) {
		return PAPIService.synch_BibsPagedGet(date, lastId, nrecs);
	}

	Mono<String> getMappedItemType(String itemTypeCode) {

		final var hostlmsCode = getHostLmsCode();

		if (hostlmsCode != null && itemTypeCode != null) {
			return referenceValueMappingService.findMapping("ItemType", "DCB",
					itemTypeCode, "ItemType", hostlmsCode)
				.map(ReferenceValueMapping::getToValue)
				.switchIfEmpty(raiseError(Problem.builder()
					.withTitle("Unable to find item type mapping from DCB to " + hostlmsCode)
					.withDetail("Attempt to find item type mapping returned empty")
					.with("Source category", "ItemType")
					.with("Source context", "DCB")
					.with("DCB item type code", itemTypeCode)
					.with("Target category", "ItemType")
					.with("Target context", hostlmsCode)
					.build())
				);
		}

		log.error(String.format("Request to map item type was missing required parameters %s/%s", hostlmsCode, itemTypeCode));
		return raiseError(Problem.builder()
			.withTitle("Request to map item type was missing required parameters")
			.withDetail(String.format("itemTypeCode=%s, hostLmsCode=%s", itemTypeCode, hostlmsCode))
			.with("Source category", "ItemType")
			.with("Source context", "DCB")
			.with("DCB item type code", itemTypeCode)
			.with("Target category", "ItemType")
			.with("Target context", hostlmsCode)
			.build());
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PolarisItemStatus {
		@JsonProperty("BannerText")
		private String bannerText;
		@JsonProperty("Description")
		private String description;
		@JsonProperty("ItemStatusID")
		private Integer itemStatusID;
		@JsonProperty("Name")
		private String name;
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
		@JsonProperty("GetBibsByIDRows")
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

	private static RuntimeException patronNotFound(String localId, String hostLmsCode) {
		return new PatronNotFoundInHostLmsException(localId, hostLmsCode);
	}

  public Mono<Boolean> supplierPreflight(String borrowingAgencyCode, String supplyingAgencyCode, String canonicalItemType, String canonicalPatronType) {
		log.debug("POLARIS Supplier Preflight {} {} {} {}",borrowingAgencyCode,supplyingAgencyCode,canonicalItemType,canonicalPatronType);
    return Mono.just(TRUE);
  }

  public boolean reflectPatronLoanAtSupplier() {
    return true;
  }

  @Override
  public Mono<String> deleteHold(DeleteCommand deleteCommand) {
		String requestId = deleteCommand.getRequestId();
		String patronId = deleteCommand.getPatronId();
		log.debug("Delete hold for Polaris. Patron ID {}, request ID {}", patronId, requestId);
		if (requestId == null || patronId == null) {
			log.error("DeleteCommand is missing required request ID or patronId. {}", deleteCommand);
			return Mono.error(new MissingParameterException("DeleteCommand missing requestId or patronId"));
		}

		// First we try to delete the hold.
		// Please be aware that if the hold is pending, active, held or shipped, Polaris will return a 400 and won't let us delete it
		// Polaris may also give us an 'unbreakable link' error when trying to delete a virtual item if we ignore this
		// So if our first deletion attempt fails, we fall back to cancelling the hold and then we try and delete it again.
		// https://qa-polaris.polarislibrary.com/Polaris.ApplicationServices/help/holdrequests/delete_holdrequest_local
		return ApplicationServices.deleteHoldRequest(requestId)
			.flatMap(deleteSucceeded -> {
				if (Boolean.TRUE.equals(deleteSucceeded)) {
					return Mono.just("OK_DELETED");
				}
				// If it's not succeeded and we don't know why, generic error
				return Mono.just("ERROR");
			})
			.onErrorResume(HttpClientResponseException.class, error -> {
				if (error.getStatus() == HttpStatus.BAD_REQUEST) {
					log.info("Delete hold {} failed (Status 400). Hold likely in non-deletable status. Attempting to cancel then re-delete.", requestId);
					return this.cancelHoldRequest(CancelHoldRequestParameters.builder()
							.patronId(patronId)
							.localRequestId(requestId)
							.build())
						.flatMap(cancelResult -> {
							log.debug("Hold cancelled (Result: {}). Retrying delete for {}.", cancelResult, requestId);
							return ApplicationServices.deleteHoldRequest(requestId);
						})
						.map(retrySucceeded -> {
							if (Boolean.TRUE.equals(retrySucceeded)) {
								return "OK_DELETED";
							}
							// Even if the second delete returns false, the hold is cancelled.
							return "OK_CANCELLED";
						})
						// Fail-safe to OK_CANCELLED: as we have tried to break the link and Polaris might just be being a bit slow.
						// This is being generous to Polaris: we may want to make this more strict in future.
						.onErrorResume(retryError -> {
							log.warn("Secondary delete failed for {}. Returning OK_CANCELLED.", requestId, retryError);
							return Mono.just("OK_CANCELLED");
						});
				}
				// Fallback if an error has fallen through somehow that we don't know about
				return Mono.error(error);
			});
	}


	@Override
	public @NonNull String getClientId() {
		
//		return Optional.ofNullable(this.applicationServicesOverrideURL())
//			.map( uri -> uri.resolve("/") )
//			.map( ovr -> "%s:%s".formatted(this.defaultBaseUrl.resolve("/").toString(), ovr.toString()) )
//			.orElseGet(this.defaultBaseUrl.resolve("/")::toString);
		
		// Probably don't need both.
		
		// Uri "toString" behaviour will sometimes return the string provided at initialization.
		// While this is OK for general operation, we need to compare values here. Resolving a relative URI
		// will force the toString method to construct a new string representation, meaning it's more comparable.
		return this.defaultBaseUrl.resolve("/").toString();
	}

	public R2dbcOperations getR2dbcOperations() {
		return r2dbcOperations;
	}
	
	@Override
	public boolean isSourceImportEnabled() {
		return isEnabled();
	}
	
	public BibsPagedGetParams mergeApiParameters(Optional<BibsPagedGetParams> parameters) {
		
		final int maxPageSize = 90;
		
		return parameters
			// Create builder from the existing params.
			.map( BibsPagedGetParams::toBuilder )
			
			// No current properties just set offset to 0
			.orElse(BibsPagedGetParams.builder()
					.lastId(0)) // Default 0
			
			// Ensure we add the constant parameters.
			.nrecs(Optional.ofNullable(polarisConfig.getPageSize())
					.map( pageSize -> {
						if (pageSize > maxPageSize) {
							log.info("Limiting POLARIS page size to {}", maxPageSize);
							return maxPageSize;
						}
						
						return pageSize;
					})
					.orElseGet(() -> {
						log.info("Defaulting page size to {}", maxPageSize);
						return maxPageSize;
					}))
			.build();
		
	}
	
  /**
   * N.B. This is now the main entry point for harvestV2
   */
	@Override
	public Mono<SourceRecordImportChunk> getChunk( Optional<JsonNode> checkpoint ) {
		try {

			// Use the inbuilt marshalling to convert into the BibParams.
			final Optional<ExtendedBibsPagedGetParams> optExtParams = checkpoint.isPresent() ? Optional.of( objectMapper.readValueFromTree(checkpoint.get(), ExtendedBibsPagedGetParams.class) ) : Optional.empty();
			final ExtendedBibsPagedGetParams extParams = optExtParams.isPresent() ? optExtParams.get() : new ExtendedBibsPagedGetParams();

			// Extract the bibs paged params needed for the HTTP request from the extended params we store against the checkpoint
			final Optional<BibsPagedGetParams> optParams = optExtParams.isPresent() ? Optional.of(optExtParams.get().toBibsPagedGetParams()) : Optional.empty();


				// Instant highestDateUpdatedSeen = optExtParams.isPresent() ? optExtParams.get().getHighestDateUpdatedSeen() : null;
				
				final BibsPagedGetParams apiParams = mergeApiParameters(optParams);
				final Instant now = Instant.now();

				log.info("Polaris get bibs page from {} with params {}",lms.getName(),apiParams);

				return Mono.just( apiParams )

					// functioned out for different implementations
					.flatMap(this::fetchBibs)

          .doOnNext ( bibsPaged -> log.info("bibsPaged {}",bibsPaged) )
					
					.flatMap( bibsPaged -> {

            if ( bibsPaged.get("PAPIErrorCode") != null )
              log.info("PAPIErrorCode: {}",bibsPaged.get("PAPIErrorCode").getValue());

            if ( bibsPaged.get("ErrorMessage") != null )
              log.info("ErrorMessage: {}",bibsPaged.get("ErrorMessage").getValue());

						 int lastId = bibsPaged.get("LastID") != null ? bibsPaged.get("LastID").getIntValue() : 0;

						 return Mono.just( bibsPaged )
							.mapNotNull( itemPage -> {
								var entries = itemPage.get("GetBibsPagedRows");
								if (entries == null) {
									log.warn("[.GetBibsPagedRows] property received from polaris is null");

									// Try the other property
									entries = itemPage.get("GetBibsByIDRows");

									if (entries == null) {
										log.warn("[.GetBibsByIDRows] property received from polaris is null");
									}
								}

								return entries;
							})
							.filter( entries -> {
								if (entries.isArray()) {
									return true;
								}
								
								log.warn("[.GetBibsPagedRows] property received from polaris is not an array");
								return false;
							})
							.cast( JsonArray.class )
							.flatMap( jsonArr -> {

								// If we have a full page, then we assume there is a subsequent page
								boolean is_last_chunk =  jsonArr.size() != apiParams.getNrecs();
								
								try {
									
									// if is_last_chunk we should set startdatemodified to the highest startdatemodified we have seen so far
									// so that in the next run we start with the most recently modified record.

									// If we don't do this, and the records go 1, 2, 3, 4, 5, 6 in the first pass, leaving next-id = 6
									// and the next page of data is 1,2,3,4,5,6.... 2,3,4,2,6 then the ingest will pick up at record 6 and skip
									// the edits for 2,3,4,4.. which will cause problems. 

									boolean isLastChunk = jsonArr.size() != apiParams.getNrecs();

									// if the last chunk had already seen the highest date modified, we've transitioned to fetching updated records
									if ( extParams.getStartdatemodified() != null && polarisConfig.isUseNewBibChunkIngest()) {
										isLastChunk = true;
									}

									log.info("Got page of size {} from polaris, requested {}, therefore isLastChunk={}",jsonArr.size(), apiParams.getNrecs(), isLastChunk);
									
									final var builder = SourceRecordImportChunk.builder()
											.lastChunk( isLastChunk );

									jsonArr.values().forEach(rawJson -> {

										try {
											builder.dataEntry( SourceRecord.builder()
												.hostLmsId( lms.getId() )
												.lastFetched( now )
												.remoteId( rawJson.get("BibliographicRecordID").coerceStringValue() )
												.sourceRecordData( rawJson )
												.build());

											Instant modification_instant = convertMSJsonDate(rawJson.get("ModificationDate").getStringValue());
											// log.info("Record modification date : {}",modification_instant);

											if ( ( modification_instant != null ) &&
                           ( ( extParams.getHighestDateUpdatedSeen() == null ) ||
											       ( extParams.getHighestDateUpdatedSeen().isBefore(modification_instant) ) ) ) {
											  extParams.setHighestDateUpdatedSeen(modification_instant);
												log.info("{} extParams.setHighestDateUpdatedSeen({})",lms.getCode(), modification_instant);
										  }

					  			} catch (Throwable t) {  				
					  				if (log.isDebugEnabled()) {
					    				log.error( "Error creating SourceRecord from JSON '{}' \ncause: {}", rawJson, t);
					  				} else {
					  					log.error( "Error creating SourceRecord from JSON", t );
					  				}
					  			}
								});

								if ( isLastChunk ) {
									// If this is the last chunk, set the highest updated date seen, null out our transient and set recno to 0;
									extParams.setStartdatemodified(extParams.getHighestDateUpdatedSeen());
									extParams.setLastId(Integer.valueOf(0));
                  extParams.setPagesInCurrentCheckpoint(Integer.valueOf(0));
                  extParams.setCheckpointDate(Instant.now());
                  extParams.setRecordsInLastPage(jsonArr.size());
                  extParams.setHostCode(lms.getCode());
								}
								else {
                  extParams.setPagesInCurrentCheckpoint(extParams.getPagesInCurrentCheckpoint() == null ? 1 : extParams.getPagesInCurrentCheckpoint().intValue() + 1 );
                  extParams.setCheckpointDate(Instant.now());
                  extParams.setRecordsInLastPage(jsonArr.size());
									extParams.setLastId(Integer.valueOf(lastId));
								}

								// We return the current data with the Checkpoint that will return the next chunk.
								final JsonNode newCheckpoint = objectMapper.writeValueToTree(extParams);
								log.info("Polaris checkpoint {} {} {}",lms.getCode(), extParams, newCheckpoint);

								builder.checkpoint( newCheckpoint );
								
								return Mono.just( builder.build() );
								
							} catch (Exception e) {
								return Mono.error( e );
							}
						});
				});
		} catch (Exception e) {
			return Mono.error( e );
		}
	}

	private Mono<JsonNode> fetchBibs(BibsPagedGetParams params) {

		if (polarisConfig.isUseNewBibChunkIngest()) {
			return synch_GetUpdatedBibsThenFetchBibs(params); // new logic
		}

		return Mono.from(PAPIService.synch_BibsPagedGetRaw(params)); // existing logic
	}


	public Mono<JsonNode> synch_GetUpdatedBibsThenFetchBibs(BibsPagedGetParams params) {

		// assume this is a full harvest on face value
		var isFullHarvest = true;

		// if the last chunk appeared we see this value..
		final var startDateModifiedPresent = params.getStartdatemodified() != null;

		// now make our assumption
		isFullHarvest = !startDateModifiedPresent;

		if (isFullHarvest) {
			// carry on as normal
			return Mono.from(PAPIService.synch_BibsPagedGetRaw(params));
		}

		// We've transitioned to fetching updated bibs

		String dateStr = Optional.of(params.getStartdatemodified())
			.map(inst -> inst.truncatedTo(ChronoUnit.MILLIS).toString())
			//    "ErrorMessage" : "SqlDateTime overflow. Must be between 1/1/1753 12:00:00 AM and 12/31/9999 11:59:59 PM.",
			.orElse(Instant.parse("1753-01-01T00:00:00Z").toString());

		log.info("get page : {} {} {}", lms.getCode(), params, dateStr);

		/*
			RESTRICTION:
			No more than 50 bibliographic records may be requested at a time. Bibliographic record IDs must be numeric.
			If a bibliographic record requested does not exist, a row will not be returned.
			https://documentation.iii.com/polaris/PAPI/7.4/PAPIService/Synch_BibsByIDGet.htm#papiservicesynchdiscovery_454418000_1271378
		*/
		int nrecs = params.getNrecs();

		if (nrecs > 50) {
			log.warn("synch_GetUpdatedBibsThenFetchBibs : nrecs > 50, setting to 50");
			nrecs = 50;
		}

		// importantly we are passing the date here so that even if there are more than 50 bibs to fetch
		// we will pick up from the last modified date next time
		return Mono.from(PAPIService.synch_GetUpdatedBibsPaged(dateStr, nrecs))
			.flatMapMany(response -> Flux.fromIterable(response.getBibIDListRows()))
			.map(PAPIClient.BibIDListRow::getBibliographicRecordID)
			.filter(Objects::nonNull)
			.take(50) // Only take the first 50 valid IDs
			.collectList()
			.flatMap(batch -> {
				if (batch.isEmpty()) {
					return Mono.just(JsonNode.nullNode()); // Nothing to fetch
				}

				String idParam = batch.stream()
					.map(String::valueOf)
					.collect(Collectors.joining(","));

				return Mono.from(PAPIService.synch_BibsByIDGetRaw(idParam));
			});
	}

	private Instant convertMSJsonDate(String msDate) {
		Instant result = null;

		if ( msDate == null )
			return null;

		try {
			Matcher matcher = msDateRegex.matcher(msDate.toLowerCase());

			if (matcher.matches()) {
				long timestamp = Long.parseLong(matcher.group(1)); // 1708419632890
				String timezoneOffset = matcher.group(2);          // -0600

				// Convert timestamp to Instant
				Instant instant = Instant.ofEpochMilli(timestamp);

				// Parse the timezone offset
				int hoursOffset = Integer.parseInt(timezoneOffset.substring(0, 3));
				int minutesOffset = Integer.parseInt(timezoneOffset.substring(0, 1) + timezoneOffset.substring(3, 5));
				ZoneOffset offset = ZoneOffset.ofHoursMinutes(hoursOffset, minutesOffset);

				// Create an OffsetDateTime
				OffsetDateTime dateTime = instant.atOffset(offset);
				result = dateTime.toInstant();
			} else {
				log.warn("Invalid Microsoft date format: {}", msDate);
			}
		}
		catch ( Exception e ) {
			log.warn("Problem parsing polaris date: {}:{}",msDate, e.getMessage());
		}
		return result;
	}

	@Override
	public BibsPagedRow convertSourceToInternalType(SourceRecord source) {
		return conversionService.convertRequired(source.getSourceRecordData(), BibsPagedRow.class);
	}

	static String getNoteForStaff(String supplierAgencyCode, String supplierHostLmsCode) {
		return "Supplier Agency Code: " + supplierAgencyCode
			+ ", \nSupplier Hostlms Code: " + supplierHostLmsCode;
	}

  @Override
  public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
		// https://stlouis-training.polarislibrary.com/Polaris.ApplicationServices/help/itemrecords/post_blocking_note
    log.info("Polaris prevent renewal {}",prc);
    return ApplicationServices.placeItemBlock(prc.getItemId(), Integer.valueOf(255),"A hold has been placed on this item by a patron at the owning library. Please do not renew")
			.then();
  }

  public Mono<PingResponse> ping() {
    Instant start = Instant.now();
    return Mono.from(ApplicationServices.test())
      .flatMap( tokenInfo -> {
        return Mono.just(PingResponse.builder()
          .target(getHostLmsCode())
          .status("OK")
          .versionInfo(getHostSystemType()+":"+getHostSystemVersion())
          .pingTime(Duration.between(start, Instant.now()))
          .build());
      })
      .onErrorResume( e -> {
        return Mono.just(PingResponse.builder()
          .target(getHostLmsCode())
          .status("ERROR")
          .versionInfo(getHostSystemType()+":"+getHostSystemVersion())
          .additional(e.getMessage())
          .pingTime(Duration.ofMillis(0))
          .build());
      })

    ;
  }

  public String getHostSystemType() {
    return "POLARIS";
  }

  public String getHostSystemVersion() {
    return "v1";
  }

  public String getHostLmsCode() {
    String result = lms.getCode();
    if ( result == null ) {
      log.warn("getCode from hostLms returned NULL : {}",lms);
    }
    return result;
  }

}

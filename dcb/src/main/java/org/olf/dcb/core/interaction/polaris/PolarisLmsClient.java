package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.unexpectedResponseProblem;
import static org.olf.dcb.core.interaction.polaris.Direction.HOST_LMS_TO_POLARIS;
import static org.olf.dcb.core.interaction.polaris.Direction.POLARIS_TO_HOST_LMS;
import static org.olf.dcb.core.interaction.polaris.MarcConverter.convertToMarcRecord;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.*;
import static org.olf.dcb.core.interaction.polaris.PolarisItem.mapItemStatus;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static reactor.function.TupleUtils.function;
import static services.k_int.utils.StringUtils.parseList;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.marc4j.marc.Record;
import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.RelativeUriResolver;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.LibraryHold;
import org.olf.dcb.core.interaction.polaris.exceptions.HoldRequestException;
import org.olf.dcb.core.interaction.shared.NoNumericRangeMappingFoundException;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.NumericPatronTypeMapper;
import org.olf.dcb.core.interaction.shared.PublisherState;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.ingest.marc.MarcIngestSource;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.ingest.model.RawSource;
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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

@Slf4j
@Prototype
public class PolarisLmsClient implements MarcIngestSource<PolarisLmsClient.BibsPagedRow>, HostLmsClient {
	private final URI rootUri;
	private final HostLms lms;
	private final HttpClient client;
	private final ProcessStateService processStateService;
	private final RawSourceRepository rawSourceRepository;
	private final ConversionService conversionService;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final NumericPatronTypeMapper numericPatronTypeMapper;
	private final IngestHelper ingestHelper;
	private final PolarisItemMapper itemMapper;
	private final PAPIClient papiClient;
	private final ApplicationServicesClient appServicesClient;
	private final List<ApplicationServicesClient.MaterialType> materialTypes = new ArrayList<>();
	private final List<PolarisItemStatus> statuses = new ArrayList<>();

	// ToDo align these URLs
  private static final URI ERR0211 = URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/0211/Polaris/UnableToCreateItem");

	@Creator
	PolarisLmsClient(@Parameter("hostLms") HostLms hostLms, @Parameter("client") HttpClient client,
		ProcessStateService processStateService, RawSourceRepository rawSourceRepository,
		ConversionService conversionService, ReferenceValueMappingService referenceValueMappingService,
		NumericPatronTypeMapper numericPatronTypeMapper, PolarisItemMapper itemMapper) {

		log.debug("Creating Polaris HostLms client for HostLms {}", hostLms);

		rootUri = UriBuilder.of((String) hostLms.getClientConfig().get(CLIENT_BASE_URL)).build();
		lms = hostLms;

		this.appServicesClient = new ApplicationServicesClient(this);
		this.papiClient = new PAPIClient(this);
		this.itemMapper = itemMapper;
		this.ingestHelper = new IngestHelper(this, hostLms, processStateService);
		this.processStateService = processStateService;
		this.rawSourceRepository = rawSourceRepository;
		this.conversionService = conversionService;
		this.referenceValueMappingService = referenceValueMappingService;
		this.numericPatronTypeMapper = numericPatronTypeMapper;
		this.client = client;
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(
		PlaceHoldRequestParameters parameters) {

		return placeHoldRequest(parameters, false);
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(
		PlaceHoldRequestParameters parameters) {

		return placeHoldRequest(parameters, true);
	}

	/**
	 * Borrower requests use a real pickup location, supplier requests will usually pass through
	 * the agency code so we have a rough idea where the item is going
	 */
	private Mono<LocalRequest> placeHoldRequest(
		PlaceHoldRequestParameters parameters, boolean isBorrower) {

		log.info("placeHoldRequest {} {}",parameters,isBorrower);

		// If this is a borrowing agency then we are placing a hold on a virtual item. The item should have been created
		// with a home location of the declared ILL location. The pickup location should be the pickup location specified
		// by the borrower.

		// if this is a supplying agency then we will be using the ILL location as the pickup location for an item that
		// already exists. The vpatron should have been created at the ILL location.

		return getBibWithItem(parameters)
			.map(tuple -> {
				final var bib = tuple.getT1();
				final var item = tuple.getT2();

				String pickup_location = getPickupLocation(parameters, isBorrower);

				log.info("Derived pickup location for hold isBorrower={} : {}",isBorrower, pickup_location);

				return HoldRequestParameters.builder()
					.localPatronId(parameters.getLocalPatronId())
					.recordNumber(parameters.getLocalItemId())
					.title(bib.getBrowseTitle())
					.primaryMARCTOMID(bib.getPrimaryMARCTOMID())
					.pickupLocation(pickup_location)
					.note(parameters.getNote())
					.dcbPatronRequestId(parameters.getPatronRequestId())
					.localItemLocationId(item.getLocationID())
					.bibliographicRecordID(bib.getBibliographicRecordID())
					.itemBarcode(item.getBarcode())
					.build();
			})
			.doOnNext( hr -> log.info("Attempt to place hold {}",hr) )
			.flatMap(appServicesClient::createHoldRequestWorkflow)
			.doOnNext( hr -> log.info("got hold response {}",hr) )
			.flatMap(function(this::getLocalHoldRequestId));
	}

	public <R> Mono<LocalRequest> getLocalHoldRequestId(String patronId, Integer bibId, String activationDate) {

		log.debug("getPatronHoldRequestId({}, {})", bibId, activationDate);

		// TEMPORARY WORKAROUND - Wait for polaris to process the hold and make it
		// visible
		synchronized (this) {
			try {
				Thread.sleep(2000);
			} catch (Exception e) {
			}
		}

		return appServicesClient.listPatronLocalHolds(patronId)
			.doOnNext(entries -> log.debug("Got Polaris Holds: {}", entries))
			.flatMapMany(Flux::fromIterable)
			.filter(holds -> shouldIncludeHold(holds, bibId, activationDate))
			.collectList()
			.flatMap(filteredHolds -> chooseHold(filteredHolds))
			// We should retrieve the item record for the selected hold and store the barcode here
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when getting Hold: {}", error.getMessage());
				return Mono.error(new HoldRequestException("Error occurred when getting Hold"));
			});
	}

	private Boolean shouldIncludeHold(
		ApplicationServicesClient.SysHoldRequest sysHoldRequest, Integer bibId, String activationDate) {

		final var zonedDateTime = ZonedDateTime.parse(sysHoldRequest.getActivationDate());
		final var formattedDate = zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		if (Objects.equals(sysHoldRequest.getBibliographicRecordID(), bibId)) {
			log.info("hold matched on bib Id.");

			return TRUE;
		}

		log.error("did not match on known bib id {} and known activation date {} with sysHoldRequest {}",
			bibId, activationDate, sysHoldRequest);
		return FALSE;
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
			throw new HoldRequestException("No hold requests found: " + filteredHolds);
		}
	}

	private static String getPickupLocation(PlaceHoldRequestParameters parameters, boolean isBorrower) {
		// Different systems will use different pickup locations - we default to passing through
		// parameters.getPickupLocationCode

		// This is the code passed through from the users selected pickup location
		String pickup_location = parameters.getPickupLocationCode();

		// However - polaris as a pickup location actually needs to use the local ID of the pickup location
		// So if we have a specific local ID, pass that down the chain instead.
		if ( isBorrower && ( parameters.getPickupLocation() != null ) ) {
			if ( parameters.getPickupLocation().getLocalId() != null )
				log.debug("Overriding pickup location code with ID from selected record");
				pickup_location = parameters.getPickupLocation().getLocalId();
		}
		return pickup_location;
	}

	private Mono<Tuple2<ApplicationServicesClient.BibliographicRecord, PAPIClient.ItemGetRow>> getBibWithItem(
		PlaceHoldRequestParameters parameters) {
		return getBibIdFromItemId(parameters.getLocalItemId())
			.flatMap(this::getBib)
			.zipWith(papiClient.synch_ItemGet(parameters.getLocalItemId()));
	}

	@Override
	public Mono<HostLmsRequest> getRequest(String localRequestId) {
		return appServicesClient.getLocalHoldRequest(Integer.valueOf(localRequestId))
			.map(hold -> HostLmsRequest.builder()
				.localId(localRequestId)
				.status(checkHoldStatus(hold.getSysHoldStatus()))
				.requestedItemId(getValue(hold, LibraryHold::getItemRecordID, Object::toString))
				.requestedItemBarcode(getValue(hold, LibraryHold::getItemBarcode))
				.build());
	}

	/**
	 * From <a href="https://qa-polaris.polarislibrary.com/polaris.applicationservices/help/sysholdstatuses/get_syshold_statuses">statuses</a>
	 */
	private String checkHoldStatus(String status) {
		log.debug("Checking hold status: {}", status);
		return switch (status) {
			case "Cancelled" -> HostLmsRequest.HOLD_CANCELLED;
			case "Pending" -> HostLmsRequest.HOLD_CONFIRMED;
			case "Held" -> HostLmsRequest.HOLD_READY;
			case "Shipped" -> HostLmsRequest.HOLD_TRANSIT;
			default -> status;
		};
	}

	@Override
	public Mono<String> createBib(Bib bib) {
		return appServicesClient.createBibliographicRecord(bib).map(String::valueOf);
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand createItemCommand) {
		log.info("createItem({})",createItemCommand);

		return appServicesClient.addItemRecord(createItemCommand)
			.doOnSuccess(r -> log.info("Got create item response from Polaris: {}",r))
			.map(itemCreateResponse -> {
				if (itemCreateResponse.getAnswerExtension() == null) {
					String messages = itemCreateResponse.getInformationMessages() != null
						? itemCreateResponse.getInformationMessages().toString()
						: "NO DETAILS";

					throw new RuntimeException("Missing answer" + messages);
				}
				return HostLmsItem.builder()
					.localId(String.valueOf(itemCreateResponse.getAnswerExtension().getAnswerData().getItemRecord().getItemRecordID()))
					.status(String.valueOf(itemCreateResponse.getAnswerExtension().getAnswerData().getItemRecord().getItemStatusID()))
					.build();
			})
			.onErrorMap( error -> { 
				log.error("Error attempting to create item {} : {}", createItemCommand, error.getMessage());
				return Problem.builder()
					.withType(ERR0211)
					.withTitle("Unable to create virtual item at polaris - pr=" + createItemCommand.getPatronRequestId() + " cit=" + createItemCommand.getCanonicalItemType())
					.withDetail(error.getMessage())
					.with("createItemCommand",createItemCommand)
					.build();
			});
	}

	@Override
	public Mono<List<Item>> getItems(BibRecord bib) {
		final var localBibId = bib.getSourceRecordId();

		// @see: https://documentation.iii.com/polaris/PAPI/7.1/PAPIService/Synch_ItemsByBibIDGet.htm
		return papiClient.synch_ItemGetByBibID(localBibId)
			.flatMapMany(Flux::fromIterable)
			.flatMap(this::setCircStatus)
			.flatMap(this::setMaterialTypeCode)
			.flatMap(result -> itemMapper.mapItemGetRowToItem(result, lms.getCode(), localBibId))
			.collectList();
	}

	@Override
	public Mono<HostLmsItem> getItem(String localItemId, String localRequestId) {
		return papiClient.synch_ItemGet(localItemId)
			.flatMap(this::setCircStatus)
			.map(result -> HostLmsItem.builder()
				.localId(String.valueOf(result.getItemRecordID()))
				.status( mapItemStatus(POLARIS_TO_HOST_LMS, result.getCircStatusName()))
				.barcode(result.getBarcode())
				.build());
	}

	@Override
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs, String localRequestId) {
		log.warn("Attempting to update an item status.");
		return switch (crs) {
			case AVAILABLE -> getCircStatusId(AVAILABLE).map(id -> updateItem(itemId, id)).thenReturn("OK");
			case TRANSIT -> getCircStatusId(TRANSFERRED).map(id -> updateItem(itemId, id)).thenReturn("OK");
			default -> Mono.just("OK").doOnSuccess(ok -> log.error("CanonicalItemState: '{}' cannot be updated.", crs));
		};
	}

	private Mono<Void> updateItem(String itemId, Integer toStatus) {
		return getItem(itemId, null)
			.map(HostLmsItem::getStatus)
			.map(status -> mapItemStatus(HOST_LMS_TO_POLARIS, status))
			.flatMap(this::getCircStatusId)
			.flatMap(fromStatus -> appServicesClient.updateItemRecord(itemId, fromStatus, toStatus));
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {

		// Look up virtual patron using generated unique ID string
		final var uniqueId = getValue(patron, org.olf.dcb.core.model.Patron::determineUniqueId);

		final var barcodeListAsString = getValue(patron, org.olf.dcb.core.model.Patron::determineHomeIdentityBarcode);
		final var firstBarcodeInList = parseList(barcodeListAsString).get(0);

		return papiClient.patronSearch(firstBarcodeInList, uniqueId)
			.map(PAPIClient.PatronSearchRow::getPatronID)
			.flatMap(appServicesClient::handlePatronBlock)
			.map(String::valueOf)
			.flatMap(this::getPatronByLocalId);
	}

	@Override
	public Mono<String> createPatron(Patron patron) {
		log.info("createPatron({}) at {}", patron, lms);

		return papiClient.synch_ItemGet(patron.getLocalItemId())
			.map(itemGetRow -> patron.setLocalItemLocationId(itemGetRow.getLocationID()))
			.flatMap(papiClient::patronRegistrationCreate)
			.flatMap(result -> validateCreatePatronResult(result, patron))
			.doOnSuccess(res -> log.debug("Successful result creating patron {}",res))
			.doOnError(error -> log.error("Problem trying to create patron",error))
			.map(PAPIClient.PatronRegistrationCreateResult::getPatronID)
			.flatMap(appServicesClient::handlePatronBlock)
			.map(String::valueOf);
	}

	private Mono<PAPIClient.PatronRegistrationCreateResult> validateCreatePatronResult(
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

		return Mono.just(result);
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		// may need to get patron or get patron defaults in order to retrieve data for updating.
		return appServicesClient.getPatronBarcode(localId)
			.flatMap(barcode -> papiClient.patronRegistrationUpdate(barcode, patronType))
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
			getHostLmsCode(), localPatronType, localId)
			.switchIfEmpty(Mono.error(new NoNumericRangeMappingFoundException(
				String.format(
					"context: %s, domain: %s, targetContext: %s, mappedValue: %s",
					getHostLmsCode(), "patronType", "DCB", localPatronType)
			)));
	};


	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		return appServicesClient.getPatron(localPatronId)
			.switchIfEmpty(Mono.defer(() -> Mono.error(patronNotFound(localPatronId, getHostLmsCode()))));
	}

	@Override
	public Mono<Patron> getPatronByUsername(String username) {

		// note: the auth controller is passing a patron barcode here
		log.info("getPatronByUsername using barcode: {}", username);
		final var barcode = username;

		return appServicesClient.getPatronIdByIdentifier(barcode, "barcode")
			.doOnSuccess(id -> log.info("getPatronByUsername found patron id: {}", id))
			.flatMap(id -> getPatronByLocalId(id))
			.switchIfEmpty(Mono.defer(() -> Mono.error(patronNotFound(username, getHostLmsCode()))));
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret) {
		return switch (authProfile) {
			case "BASIC/BARCODE+PIN" -> papiClient.patronValidate(patronPrinciple, secret);
			case "BASIC/BARCODE+PASSWORD" -> papiClient.patronValidate(patronPrinciple, secret);
			default -> Mono.empty();
		};
	}

	@Override
	public Mono<String> checkOutItemToPatron(String itemId, String patronBarcode, String localRequestId) {
		return appServicesClient.getItemBarcode(itemId)
			.flatMap(itemBarcode -> papiClient.itemCheckoutPost(itemBarcode, patronBarcode))
			.map(itemCheckoutResult -> "OK");
	}

	private Mono<LocalRequest> getPlaceHoldRequestData(Integer holdRequestId) {
		log.info("Get hold request data for {}",holdRequestId);

		return appServicesClient.getLocalHoldRequest(holdRequestId)
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
				.localStatus(getValue(response, LibraryHold::getSysHoldStatus, Object::toString, ""))
				.requestedItemId(getValue(response, LibraryHold::getItemRecordID, Object::toString))
				.requestedItemBarcode(getValue(response, LibraryHold::getItemBarcode))
				.build());
	}

	private Mono<ApplicationServicesClient.BibliographicRecord> getBib(String localBibId) {
		return appServicesClient.getBibliographicRecordByID(localBibId);
	}

	private Mono<String> getBibIdFromItemId(String recordNumber) {
		return papiClient.synch_ItemGet(recordNumber)
			.map(PAPIClient.ItemGetRow::getBibliographicRecordID)
			.map(String::valueOf);
	}

	private Mono<PAPIClient.ItemGetRow> setMaterialTypeCode(PAPIClient.ItemGetRow itemGetRow) {
		return (materialTypes.isEmpty() ? appServicesClient.listMaterialTypes().doOnNext(materialTypes::addAll)
			: Mono.just(materialTypes))
			.flatMapMany(Flux::fromIterable)
			.filter(materialType -> itemGetRow.getMaterialType().equals(materialType.getDescription()))
			.map(materialType -> String.valueOf(materialType.getMaterialTypeID()))
			.next()
			.doOnSuccess(itemGetRow::setMaterialTypeID)
			.thenReturn(itemGetRow);
	}

	private Mono<PAPIClient.ItemGetRow> setCircStatus(PAPIClient.ItemGetRow itemGetRow) {
		final var circStatus = itemGetRow.getCircStatus();
		return (statuses.isEmpty()
				? appServicesClient.listItemStatuses().doOnNext(statuses::addAll)
				: Mono.just(statuses))
			.flatMapMany(Flux::fromIterable)
			.filter(status -> circStatus.equals(status.getDescription()))
			.next()
			.map(polarisItemStatus -> {
				itemGetRow.setCircStatusID(polarisItemStatus.getItemStatusID());
				itemGetRow.setCircStatusName(polarisItemStatus.getName());
				itemGetRow.setCircStatusBanner(polarisItemStatus.getBannerText());
				return itemGetRow;
			});
	}

	private Mono<Integer> getCircStatusId(String circStatusName) {
		return (statuses.isEmpty()
					? appServicesClient.listItemStatuses().doOnNext(statuses::addAll)
					: Mono.just(statuses))
			.flatMapMany(Flux::fromIterable)
			.filter(status -> circStatusName.equals(status.getName()))
			.next()
			.map(PolarisItemStatus::getItemStatusID);
	}

	/**
	 * Make HTTP request to a Polaris system
	 *
	 * @param request Request to send
	 * @return Deserialized response body or error
	 * @param <T> Type to deserialize the response to
	 */
	<T> Mono<HttpResponse<T>> exchange(MutableHttpRequest<?> request, Class<T> returnClass) {
		return Mono.from(client.exchange(request, returnClass))
		// This has to go after more specific error handling
		// as will convert any client response exception to a problem
			.onErrorMap(HttpClientResponseException.class, responseException ->
				unexpectedResponseProblem(responseException, request, getHostLmsCode()));
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
			// Additional request specific error handling
			.transform(errorHandlingTransformer)
			// This has to go after more specific error handling
			// as will convert any client response exception to a problem
			.onErrorMap(HttpClientResponseException.class, responseException ->
				unexpectedResponseProblem(responseException, request, getHostLmsCode()));
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
		return Mono.just(UriBuilder.of(path).build())
			.map(this::resolve)
			.map(resolvedUri -> HttpRequest.<T>create(method, resolvedUri.toString()).accept(APPLICATION_JSON));
	}

	URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(rootUri, relativeURI);
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
	public Mono<String> deleteItem(String id) {
		// workflow POST delete
		// workflow PUT continue delete
		// workflow PUT don't delete bib if last item
		// ERROR PolarisWorkflowException
		return appServicesClient.deleteItemRecord(id).thenReturn("OK").defaultIfEmpty("ERROR");
	}

	@Override
	public Mono<String> deleteBib(String id) {
		// workflow POST delete
		// workflow PUT continue delete
		// ERROR PolarisWorkflowException
		return appServicesClient.deleteBibliographicRecord(id).thenReturn("OK").defaultIfEmpty("ERROR");
	}

	@Override
	public String getDefaultControlIdNamespace() {
		return lms.getName();
	}

	@Override
	public Publisher<BibsPagedRow> getResources(Instant since, Publisher<String> terminator) {
		log.info("Fetching MARC JSON from Polaris for {}", lms.getName());

		final Map<String, Object> conf = lms.getClientConfig();
		Integer pageSize = (Integer) conf.get(MAX_BIBS);
		if (pageSize > 100) {
			log.info("Limiting POLARIS page size to 100");
			pageSize = 100;
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
		return papiClient.synch_BibsPagedGet(date, lastId, nrecs);
	}

	public String getGeneralUriParameters(PolarisClient polarisClient) {
		// LinkedHashMap used to keep order of params
		final var params = new LinkedHashMap<>();

		switch (polarisClient) {
			case PAPIService -> {
				final var papiMap = getPAPIConfig();
				params.put("version", papiMap.get(PAPI_VERSION));
				params.put("langId", papiMap.get(PAPI_LANG_ID));
				params.put("appId", papiMap.get(PAPI_APP_ID));
				params.put("orgId", papiMap.get(PAPI_ORG_ID));
			}
			case APPLICATION_SERVICES -> {
				final var servicesConfig = getServicesConfig();

				params.put("version", servicesConfig.get(SERVICES_VERSION));
				params.put("language", servicesConfig.get(SERVICES_LANGUAGE));
				params.put("product", servicesConfig.get(SERVICES_PRODUCT_ID));
				params.put("domain", servicesConfig.get(SERVICES_SITE_DOMAIN));
				params.put("org", servicesConfig.get(SERVICES_ORG_ID));
				params.put("workstation", servicesConfig.get(SERVICES_WORKSTATION_ID));
			}
			default -> {
				log.error("Unknown or unsupported enum value");
				return null;
			}
		}

		if (params.values().stream().anyMatch(Objects::isNull)) {
			log.error("One or more parameter values are null: params={}", params);
		}

		return params.values().stream().map(s -> "/" + s).collect(Collectors.joining());
	}

	static <T> T extractMapValueWithDefault(Map<String, Object> map, String key, Class<T> type, Object defval) {
		final Object r1 = extractMapValue(map,key,type);
		return type.cast( r1 != null ? r1 : defval );
	}

	static <T> T extractMapValue(Map<String, Object> map, String key, Class<T> type) {
		Object value = map.get(key);

		if (value != null) {
			if (type.isInstance(value)) {
				return type.cast(value);
			} else {
				if (type == Integer.class && value instanceof String) {
					return type.cast(Integer.valueOf((String) value));
				}
			}
		}

		log.error("Unable to extract key: {}, from map: {}, to type: {}", key, map, type);
		return null;
	}

	Mono<String> getMappedItemType(String itemTypeCode) {
		if (getHostLmsCode() != null && itemTypeCode != null) {
			return referenceValueMappingService.findMapping("ItemType", "DCB",
				itemTypeCode, "ItemType", getHostLmsCode())
			.map(ReferenceValueMapping::getToValue)
			.defaultIfEmpty("19");
		}

		log.warn("Request to map item type was missing required parameters {}/{}", getHostLmsCode(), itemTypeCode);
		return Mono.just("19");
	}

	Map<String, Object> getServicesConfig() {
		return (Map<String, Object>) getConfig().get(SERVICES);
	}

	Map<String, Object> getItemConfig() {
		return (Map<String, Object>) getConfig().get(ITEM);
	}

	private Map<String, Object> getPAPIConfig() {
		return (Map<String, Object>) getConfig().get(PAPI);
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

	public enum PolarisClient { PAPIService, APPLICATION_SERVICES }

	private static RuntimeException patronNotFound(String localId, String hostLmsCode) {
		return new PatronNotFoundInHostLmsException(localId, hostLmsCode);
	}

  public Mono<Boolean> supplierPreflight(String borrowingAgencyCode, String supplyingAgencyCode, String canonicalItemType, String canonicalPatronType) {
		log.debug("POLARIS Supplier Preflight {} {} {} {}",borrowingAgencyCode,supplyingAgencyCode,canonicalItemType,canonicalPatronType);
    return Mono.just(TRUE);
  }

  public boolean reflectPatronLoanAtSupplier() {
    return false;
  }

}

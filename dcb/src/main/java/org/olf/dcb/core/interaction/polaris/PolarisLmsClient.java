package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.unexpectedResponseProblem;
import static org.olf.dcb.core.interaction.polaris.Direction.POLARIS_TO_HOST_LMS;
import static org.olf.dcb.core.interaction.polaris.MarcConverter.convertToMarcRecord;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.*;
import static org.olf.dcb.core.interaction.polaris.PolarisItem.mapItemStatus;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrDefault;
import static reactor.function.TupleUtils.function;
import static services.k_int.utils.StringUtils.parseList;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.http.*;
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
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronCirculationBlocksResult;
import org.olf.dcb.core.interaction.polaris.exceptions.HoldRequestException;
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

	// ToDo align these URLs
  private static final URI ERR0211 = URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/0211/Polaris/UnableToCreateItem");
	private static final String DCB_BORROWING_FLOW = "DCB";
	private static final String ILL_BORROWING_FLOW = "ILL";
	private final PolarisConfig polarisConfig;

	@Creator
	PolarisLmsClient(@Parameter("hostLms") HostLms hostLms, @Parameter("client") HttpClient client,
		ProcessStateService processStateService, RawSourceRepository rawSourceRepository,
		ConversionService conversionService, ReferenceValueMappingService referenceValueMappingService,
		NumericPatronTypeMapper numericPatronTypeMapper, PolarisItemMapper itemMapper) {

		log.debug("Creating Polaris HostLms client for HostLms {}", hostLms);

		this.lms = hostLms;
		this.polarisConfig = convertConfig(hostLms);
		this.defaultBaseUrl = UriBuilder.of(polarisConfig.getBaseUrl()).build();
		this.applicationServicesOverrideURL = applicationServicesOverrideURL();
		this.ApplicationServices = new ApplicationServicesClient(this, polarisConfig);
		this.PAPIService = new PAPIClient(this, polarisConfig);
		this.itemMapper = itemMapper;
		this.ingestHelper = new IngestHelper(this, hostLms, processStateService);
		this.processStateService = processStateService;
		this.rawSourceRepository = rawSourceRepository;
		this.conversionService = conversionService;
		this.referenceValueMappingService = referenceValueMappingService;
		this.numericPatronTypeMapper = numericPatronTypeMapper;
		this.client = client;
	}

	private PolarisConfig convertConfig(HostLms hostLms) {
		return new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.convertValue(hostLms.getClientConfig(), PolarisConfig.class);
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

		final var borrowerlendingFlow = borrowerlendingFlow();
		if (borrowerlendingFlow == null) {
			return placeHoldRequest(parameters, TRUE);
		}

		return switch (borrowerlendingFlow) {
			case DCB_BORROWING_FLOW -> placeHoldRequest(parameters, TRUE);
			case ILL_BORROWING_FLOW -> placeILLHoldRequest(polarisConfig.getIllLocationId(), parameters);
			default -> placeHoldRequest(parameters, TRUE);
		};
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

	private Mono<Integer> getILLRequestId(String patronLocalId, String title) {

		// TEMPORARY WORKAROUND - Wait for polaris to process the hold and make it
		// visible
		synchronized (this) {
			try {
				Thread.sleep(2000);
			} catch (Exception e) {
			}
		}

		return ApplicationServices.getIllRequest(patronLocalId)
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

		// TEMPORARY WORKAROUND - Wait for polaris to process the hold and make it
		// visible
		synchronized (this) {
			try {
				Thread.sleep(2000);
			} catch (Exception e) {
			}
		}

		return ApplicationServices.listPatronLocalHolds(patronId)
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
		PlaceHoldRequestParameters parameters, boolean isBorrower) {

		log.info("placeHoldRequest {} {}", parameters, isBorrower);

		// If this is a borrowing agency then we are placing a hold on a virtual item. The item should have been created
		// with a home location of the declared ILL location. The pickup location should be the pickup location specified
		// by the borrower.

		// if this is a supplying agency then we will be using the ILL location as the pickup location for an item that
		// already exists. The vpatron should have been created at the ILL location.

		return getBibWithItem(parameters)
			.map(tuple -> {
				final var bib = tuple.getT1();
				final var item = tuple.getT2();

				String pickupLocation = getPickupLocation(parameters, isBorrower);

				log.info("Derived pickup location for hold isBorrower={} : {}", isBorrower, pickupLocation);

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
			.flatMap(function(this::getLocalHoldRequestId));
	}

	public Mono<LocalRequest> getLocalHoldRequestId(
		String patronId, Integer bibId, String activationDate, String note) {

		log.debug("getPatronHoldRequestId({}, {})", bibId, activationDate);

		// TEMPORARY WORKAROUND - Wait for polaris to process the hold and make it
		// visible
		synchronized (this) {
			try {
				Thread.sleep(2000);
			} catch (Exception e) {
			}
		}

		return ApplicationServices.listPatronLocalHolds(patronId)
			.doOnNext( logLocalHolds() )
			.flatMapMany(Flux::fromIterable)
			.filter(holds -> shouldIncludeHold(holds, bibId, activationDate, note))
			.collectList()
			.flatMap(this::chooseHold)
			// We should retrieve the item record for the selected hold and store the barcode here
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when getting Hold: {}", error.getMessage());
				return Mono.error(new HoldRequestException("Error occurred when getting Hold"));
			});

	}

	private static Consumer<List<ApplicationServicesClient.SysHoldRequest>> logLocalHolds() {
		return entries -> log.debug("Retrieved {} local holds: {}", entries.size(), entries);
	}

	private Boolean shouldIncludeHold(ApplicationServicesClient.SysHoldRequest sysHoldRequest,
		Integer bibId, String activationDate, String note) {

		if (Objects.equals(sysHoldRequest.getBibliographicRecordID(), bibId) &&
			isEqualDisplayNoteIfPresent(sysHoldRequest, note) &&
			isEqualActivationDateIfPresent(sysHoldRequest, activationDate)) {

			log.info("Hold boolean matched.");

			return TRUE;
		}
		
		return FALSE;
	}

	private static boolean isEqualActivationDateIfPresent(
		ApplicationServicesClient.SysHoldRequest sysHoldRequest, String activationDate) {

		if (sysHoldRequest.getActivationDate() != null && activationDate != null) {
			final var zonedDateTime = ZonedDateTime.parse(sysHoldRequest.getActivationDate());
			final var formattedDate = zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
			return Objects.equals(activationDate, formattedDate);
		}

		return TRUE;
	}

	private static boolean isEqualDisplayNoteIfPresent(
		ApplicationServicesClient.SysHoldRequest sysHoldRequest, String note) {

		final var returnedDisplayNote = notNullDisplayNote(sysHoldRequest);

		if (returnedDisplayNote != null && note != null) {
			return Objects.equals(returnedDisplayNote, note);
		}

		return TRUE;
	}

	private static String notNullDisplayNote(ApplicationServicesClient.SysHoldRequest sysHoldRequest) {
		return sysHoldRequest.getPacDisplayNotes() != null ? sysHoldRequest.getPacDisplayNotes() : null;
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

	private String getPickupLocation(PlaceHoldRequestParameters parameters, boolean isBorrower) {
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

		// supplier requests need the pickup location to be set as ILL
		if (isBorrower == FALSE) {
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
			.zipWith(ApplicationServices.itemrecords(parameters.getLocalItemId()));
	}

	@Override
	public Mono<HostLmsRequest> getRequest(String localRequestId) {

		return parseLocalRequestId(localRequestId)
			.flatMap(ApplicationServices::getLocalHoldRequest)
			.map(hold -> HostLmsRequest.builder()
				.localId(localRequestId)
				.status(checkHoldStatus(hold.getSysHoldStatus()))
				.rawStatus(hold.getSysHoldStatus())
				.requestedItemId(getValue(hold, LibraryHold::getItemRecordID, Object::toString))
				.requestedItemBarcode(getValue(hold, LibraryHold::getItemBarcode))
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
			.flatMap(result -> itemMapper.mapItemGetRowToItem(result, lms.getCode(), localBibId))
			.collectList();
	}

	@Override
	public Mono<HostLmsItem> getItem(String localItemId, String localRequestId) {

		return parseLocalItemId(localItemId)
			.flatMap(ApplicationServices::itemrecords)
			.doOnSuccess(itemRecordFull -> log.info("Got item: {}", itemRecordFull))
			.map(item -> validate(localItemId, item))
			.flatMap(this::collectItemStatusName)
			.map(itemRecord -> {
				final var hostLmsStatus = mapItemStatus(POLARIS_TO_HOST_LMS, itemRecord.getItemStatusName());

				return HostLmsItem.builder()
					.localId(String.valueOf(itemRecord.getItemRecordID()))
					.status(hostLmsStatus)
					.rawStatus(itemRecord.getItemStatusName())
					.barcode(itemRecord.getBarcode())
					.build();
			});
	}

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
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs, String localRequestId) {
		log.warn("Attempting to update an item status.");
		
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

		return ApplicationServices.itemrecords(itemId)
			.flatMap(item -> ApplicationServices.updateItemRecord(itemId, item.getItemStatusID(), toStatus));
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		final var barcodeListAsString = getValue(patron,
			org.olf.dcb.core.model.Patron::determineHomeIdentityBarcode);

		final var firstBarcodeInList = parseList(barcodeListAsString).get(0);

		// note: if a patron isn't found a empty mono will need to be returned
		// to then create the patron
		return PAPIService.patronSearch(firstBarcodeInList)
			.map(PAPIClient.PatronSearchRow::getPatronID)
			.flatMap(this::foundVirtualPatron);
	}

	private Mono<Patron> foundVirtualPatron(Integer patronId) {
		log.info("Found virtual patron with local id: {}", patronId);

		return ApplicationServices.handlePatronBlock(patronId)
			.map(String::valueOf)
			.flatMap(ApplicationServices::getPatron)
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
		return ApplicationServices.itemrecords(patron.getLocalItemId())
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
		return ApplicationServices.getPatron(localPatronId)
			.flatMap(this::enrichWithCanonicalPatronType)
			.zipWhen(this::getPatronCirculationBlocks, PolarisLmsClient::isBlocked)
			.switchIfEmpty(Mono.defer(() -> Mono.error(patronNotFound(localPatronId, getHostLmsCode()))));
	}

	private Mono<Patron> enrichWithCanonicalPatronType(Patron patron) {
		return numericPatronTypeMapper.mapLocalPatronTypeToCanonical(getHostLmsCode(),
				patron.getLocalPatronType(), patron.getFirstLocalId())
			.map(patron::setCanonicalPatronType)
			.defaultIfEmpty(patron);
	}

	private Mono<PatronCirculationBlocksResult> getPatronCirculationBlocks(Patron patron) {
		log.info("getPatronCirculationBlocks: {}", patron);

		final var barcode = getValue(patron, Patron::getFirstBarcode);

		return PAPIService.getPatronCirculationBlocks(barcode);
	}

	private static Patron isBlocked(Patron patron, PatronCirculationBlocksResult blocks) {
		final var canCirculate = getValueOrDefault(blocks,
			PatronCirculationBlocksResult::getCanPatronCirculate, true);

		return patron.setBlocked(!canCirculate);
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
	public Mono<String> checkOutItemToPatron(String itemId, String itemBarcode,
		String patronId, String patronBarcode, String localRequestId) {

		log.info("checkOutItemToPatron({}, {}, {})", itemId, patronBarcode, localRequestId);

		return Mono.zip(
			ApplicationServices.getItemBarcode(itemId),
			ApplicationServices.getPatronBarcode(patronId)
			)
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
				.requestedItemId(getValue(response, LibraryHold::getItemRecordID, Object::toString))
				.requestedItemBarcode(getValue(response, LibraryHold::getItemBarcode))
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
		return ApplicationServices.itemrecords(recordNumber)
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
			.onErrorResume(HttpClientResponseException.class, responseException -> {
				if (useGenericHttpClientResponseExceptionHandler) {
					// Generic error handling
					return Mono.error(unexpectedResponseProblem(responseException, request, getHostLmsCode()));
				}
				return Mono.error(responseException); // Propagate the error
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
			// Additional request specific error handling
			.transform(errorHandlingTransformer)
			// This has to go after more specific error handling
			// as will convert any client response exception to a problem
			.doOnError(HttpClientResponseException.class, error -> log.error("Unexpected response from Host LMS: {}", getHostLmsCode(), error))
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
	public Mono<String> deleteItem(String id) {
		// workflow POST delete
		// workflow PUT continue delete
		// workflow PUT don't delete bib if last item
		// ERROR PolarisWorkflowException
		return ApplicationServices.deleteItemRecord(id).thenReturn("OK").defaultIfEmpty("ERROR");
	}

	@Override
	public Mono<String> deleteBib(String id) {
		// workflow POST delete
		// workflow PUT continue delete
		// ERROR PolarisWorkflowException
		return ApplicationServices.deleteBibliographicRecord(id).thenReturn("OK").defaultIfEmpty("ERROR");
	}

	@Override
	public String getDefaultControlIdNamespace() {
		return lms.getName();
	}

	@Override
	public Publisher<BibsPagedRow> getResources(Instant since, Publisher<String> terminator) {
		log.info("Fetching MARC JSON from Polaris for {}", lms.getName());

		Integer pageSize = polarisConfig.getPageSize();
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
		return PAPIService.synch_BibsPagedGet(date, lastId, nrecs);
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
  public Mono<String> deleteHold(String id) {
		log.info("Delete hold is not currently implemented for Polaris");
    return Mono.just("OK");
  }


}

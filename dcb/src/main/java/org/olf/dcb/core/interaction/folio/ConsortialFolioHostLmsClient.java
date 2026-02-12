package org.olf.dcb.core.interaction.folio;

import static graphql.com.google.common.base.Strings.isNullOrEmpty;
import static io.micronaut.core.type.Argument.VOID;
import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static io.micronaut.http.HttpMethod.GET;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.HttpMethod.PUT;
import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.Boolean.TRUE;
import static java.util.function.Function.identity;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_LOANED;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_ON_HOLDSHELF;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_TRANSIT;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_PLACED;
import static org.olf.dcb.core.interaction.HttpProtocolToLogMessageMapper.toLogOutput;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.unexpectedResponseProblem;
import static org.olf.dcb.core.interaction.folio.CqlQuery.exactEqualityQuery;
import static org.olf.dcb.core.model.FunctionalSettingType.VIRTUAL_PATRON_NAMES_VISIBLE;
import static org.olf.dcb.core.model.WorkflowConstants.EXPEDITED_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrThrow;
import static services.k_int.utils.ReactorUtils.raiseError;
import static services.k_int.utils.StringUtils.parseList;
import static services.k_int.utils.UUIDUtils.dnsUUID;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.interaction.CannotPlaceRequestProblem;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.DeleteCommand;
import org.olf.dcb.core.interaction.FailedToGetItemsException;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.HostLmsRenewal;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.HttpResponsePredicates;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.MultipleVirtualPatronsFound;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.PreventRenewalCommand;
import org.olf.dcb.core.interaction.RelativeUriResolver;
import org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem;
import org.olf.dcb.core.interaction.VirtualPatronNotFound;
import org.olf.dcb.core.interaction.shared.MissingParameterException;
import org.olf.dcb.core.interaction.shared.NoItemTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.NoHomeBarcodeException;
import org.olf.dcb.core.model.NoHomeIdentityException;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.reactivestreams.Publisher;
import org.zalando.problem.ThrowableProblem;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class ConsortialFolioHostLmsClient implements HostLmsClient {
	// These are the same config keys as from FolioOaiPmhIngestSource
	// which was implemented prior to this client
	private static final HostLmsPropertyDefinition BASE_URL_SETTING
		= urlPropertyDefinition("base-url", "Base URL of the FOLIO system", TRUE);
	private static final HostLmsPropertyDefinition API_KEY_SETTING
		= stringPropertyDefinition("apikey", "API key for this FOLIO tenant", TRUE);

	private static final List<String> itemStatuses = List.of(
		"Aged to lost",
		"Available",
		"Awaiting pickup",
		"Awaiting delivery",
		"Checked out",
		"Claimed returned",
		"Declared lost",
		"In process",
		"In process (non-requestable)",
		"In transit",
		"Intellectual item",
		"Long missing",
		"Lost and paid",
		"Missing",
		"On order",
		"Paged",
		"Restricted",
		"Order closed",
		"Unavailable",
		"Unknown",
		"Withdrawn");

	private final HostLms hostLms;

	private final HttpClient httpClient;

	private final ConsortialFolioItemMapper consortialFolioItemMapper;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final ConversionService conversionService;
	private final ConsortiumService consortiumService;

	private final String apiKey;
	private final URI rootUri;



	public ConsortialFolioHostLmsClient(@Parameter HostLms hostLms,
		@Parameter("client") HttpClient httpClient,
		ConsortialFolioItemMapper consortialFolioItemMapper,
		ReferenceValueMappingService referenceValueMappingService,
		ConversionService conversionService, ConsortiumService consortiumService) {

		this.hostLms = hostLms;
		this.httpClient = httpClient;

		this.consortialFolioItemMapper = consortialFolioItemMapper;
		this.referenceValueMappingService = referenceValueMappingService;

		this.apiKey = API_KEY_SETTING.getRequiredConfigValue(hostLms);
		this.rootUri = UriBuilder.of(BASE_URL_SETTING.getRequiredConfigValue(hostLms)).build();
		this.conversionService = conversionService;
		this.consortiumService = consortiumService;
	}

	@Override
	public HostLms getHostLms() {
		return hostLms;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(
			BASE_URL_SETTING,
			API_KEY_SETTING
		);
	}

	@Override
	public Mono<List<Item>> getItems(BibRecord bib) {
		return getHoldings(bib.getSourceRecordId())
			.flatMap(outerHoldings -> checkResponse(outerHoldings, bib.getSourceRecordId()))
			.mapNotNull(OuterHoldings::getHoldings)
			.flatMapMany(Flux::fromIterable)
			.flatMap(this::mapHoldingsToItems)
			.collectList();
	}

	private Mono<OuterHoldings> getHoldings(String instanceId) {
		final var request = authorisedRequest(GET, "/rtac")
			.uri(uriBuilder -> uriBuilder
				.queryParam("instanceIds", instanceId)
				// Full periodicals refers to items, without this parameter holdings will be returned instead of items
				.queryParam("fullPeriodicals", true)
			);

		return makeRequest(request, Argument.of(OuterHoldings.class));
	}

	private Mono<OuterHoldings> checkResponse(OuterHoldings outerHoldings, String instanceId) {
		if (hasNoErrors(outerHoldings)) {
			if (hasNoOuterHoldings(outerHoldings)) {
				log.error("No errors or outer holdings returned from RTAC for instance ID: {}, response: {}, "
						+ "likely to be invalid API key for host LMS: {}",
					instanceId, outerHoldings, getHostLmsCode());

				// RTAC returns no outer holdings (instances) when the API key is invalid
				return Mono.error(new LikelyInvalidApiKeyException(instanceId, getHostLmsCode()));
			} else {
				if (hasMultipleOuterHoldings(outerHoldings)) {
					log.error("Unexpected outer holdings (instances) received from RTAC for instance ID: {} from Host LMS: {}, response: {}",
						instanceId, getHostLmsCode(), outerHoldings);

					// DCB only asks for holdings for a single instance at a time
					// RTAC should never respond with multiple outer holdings (instances)
					return Mono.error(new UnexpectedOuterHoldingException(instanceId, getHostLmsCode()));
				}
				else {
					return Mono.just(outerHoldings);
				}
			}
		} else {
			log.debug("Errors received from RTAC: {} for instance ID: {} for Host LMS: {}",
				outerHoldings.getErrors(), instanceId, getHostLmsCode());

			// DCB cannot know in advance whether an instance has any associated holdings / items
			// Holdings not being found for an instance is a false negative
			if (allErrorsAreHoldingsNotFound(outerHoldings)) {
				return Mono.just(outerHoldings);
			}
			else {
				log.error("Failed to get items for instance ID: {} from Host LMS: {}, errors: {}",
					instanceId, getHostLmsCode(), outerHoldings.getErrors());

				return Mono.error(new FailedToGetItemsException(instanceId, getHostLmsCode()));
			}
		}
	}

	private static boolean hasNoErrors(OuterHoldings outerHoldings) {
		return isEmpty(outerHoldings.getErrors());
	}

	private static boolean hasNoOuterHoldings(OuterHoldings outerHoldings) {
		return isEmpty(outerHoldings.getHoldings());
	}

	private static boolean hasMultipleOuterHoldings(OuterHoldings outerHoldings) {
		return outerHoldings.getHoldings().size() > 1;
	}

	private static boolean allErrorsAreHoldingsNotFound(OuterHoldings outerHoldings) {
		return outerHoldings.getErrors()
			.stream()
			.filter(error -> isNotEmpty(error.getMessage()))
			.allMatch(error -> error.getMessage().contains("Holdings not found for instance"));
	}

	private Flux<Item> mapHoldingsToItems(OuterHolding outerHoldings) {		
		return Mono.justOrEmpty(outerHoldings.getHoldings())
			.flatMapMany(Flux::fromIterable)
			// When RTAC encounters a holdings record without any items
			// it includes the holdings record in the response
			// DCB is only interested in items and thus needs to differentiate between
			// holdings in the response that are items and those that are only holdings
			// For more information on the flow inside RTAC - https://github.com/folio-org/mod-rtac/blob/3e7f25445ff79b60690fa2025f3a426d9e57fd21/src/main/java/org/folio/mappers/FolioToRtacMapper.java#L112
			//
			// Holdings without a status should be tolerated
			.filter(holdings -> itemStatuses.contains(getValue(holdings, Holding::getStatus, "Unknown")))
			.flatMap(holding -> consortialFolioItemMapper.mapHoldingToItem(holding,
				outerHoldings.getInstanceId(), getHostLmsCode()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(
		PlaceHoldRequestParameters parameters) {

		log.debug("placeHoldRequestAtSupplyingAgency({})", parameters);

		final var transactionId = UUID.randomUUID().toString();

		final var agencyCode = getValueOrNull(parameters.getPickupAgency(), Agency::getCode);
		final var firstBarcodeInList = parseList(parameters.getLocalPatronBarcode()).get(0);
		final var libraryCode = getLibraryCode(parameters);
		final var servicePointName = getValueOrNull(parameters.getPickupLocation(), Location::getPrintLabel);
		return consortiumService.isEnabled(VIRTUAL_PATRON_NAMES_VISIBLE)
			.flatMap(namesVisible -> {
				// Conditionally build the patron object, only including the names if setting explicitly enabled
				var patronBuilder = CreateTransactionRequest.Patron.builder()
					.id(parameters.getLocalPatronId())
					.barcode(firstBarcodeInList)
					.group(parameters.getLocalPatronType()); // Needs to be double quoted, otherwise errors in the folio call if there are reserved characters, eg. bracket

				if (namesVisible) {
					patronBuilder.localNames(parameters.getLocalNames());
				}
				final var request = authorisedRequest(POST, "/dcbService/transactions/" + transactionId)
					.body(CreateTransactionRequest.builder()
						.role("LENDER")
						.item(CreateTransactionRequest.Item.builder()
							.id(parameters.getLocalItemId())
							.barcode(parameters.getLocalItemBarcode())
							.build())
						.patron(patronBuilder.build())
						.pickup(CreateTransactionRequest.Pickup.builder()
							.servicePointId(dnsUUID("FolioServicePoint:" + agencyCode).toString())
							.servicePointName(servicePointName)
							.libraryCode(libraryCode)
							.build())
						.build());
				return createTransaction(request)
					.map(response -> LocalRequest.builder()
						.localId(transactionId)
						.localStatus(HOLD_CONFIRMED)
						.rawLocalStatus(response.getStatus())
						.build());
			});
	}

	private String getLibraryCode(PlaceHoldRequestParameters parameters) {
		if ( parameters.getPickupLibrary() == null )
			return "NoPickLibC= "+parameters.getPickupLocationCode()+"a="+ 
				( parameters.getPickupAgency() != null ? parameters.getPickupAgency().getCode() : "Missing Pickup Agency" );
		else
			return parameters.getPickupLibrary().getAbbreviatedName();
	}

	private Mono<CreateTransactionResponse> createTransaction(
		MutableHttpRequest<CreateTransactionRequest> request) {

		return makeRequest(request, Argument.of(CreateTransactionResponse.class),
			response -> response
				.onErrorMap(HttpResponsePredicates::isUnprocessableContent,
					error -> interpretValidationError(error, request))
				.onErrorMap(HttpResponsePredicates::isNotFound,
					error -> interpretValidationError(error, request)));
	}

	private CannotPlaceRequestProblem interpretValidationError(Throwable error,
		MutableHttpRequest<CreateTransactionRequest> request) {

		log.debug("Received validation error", error);

		if (error instanceof HttpClientResponseException clientResponseException) {
			final var response = clientResponseException.getResponse();

			final var firstError = response
				.getBody(Argument.of(ValidationError.class))
				.map(ValidationError::getFirstError)
				.orElse("Unknown validation error");

			return new CannotPlaceRequestProblem(getHostLmsCode(),
				firstError, clientResponseException, request);
		}
		else {
			return new CannotPlaceRequestProblem(getHostLmsCode(),
				"Unknown validation error", null, request);
		}
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtBorrowingAgency({})", parameters);

		final var transactionId = UUID.randomUUID().toString();
		final var isPickupAnywhereRequest = isFollowingWorkflow(parameters, PICKUP_ANYWHERE_WORKFLOW);
		final var isExpeditedCheckoutRequest = isFollowingWorkflow(parameters, EXPEDITED_WORKFLOW);

		if (isPickupAnywhereRequest) {
			log.debug("RET-PUA detected, constructing borrowing transaction request for pickup anywhere workflow");

			return constructBorrowingTransactionRequest(parameters, transactionId)
				.flatMap(this::createTransaction)
				.map(response -> LocalRequest.builder()
					.localId(transactionId)
					.localStatus(HOLD_PLACED)
					.build());
		}
		else if (isExpeditedCheckoutRequest) {
			log.debug("Expedited checkout detected, constructing transaction with role BORROWER");
			return constructBorrowingTransactionRequest(parameters, transactionId)
			.flatMap(this::createTransaction)
			.map(response -> LocalRequest.builder()
				.localId(transactionId)
				.localStatus(HOLD_PLACED)
				.build());
		}
		else return constructBorrowing_PickupTransactionRequest(parameters, transactionId)
			.flatMap(this::createTransaction)
			.map(response -> LocalRequest.builder()
				.localId(transactionId)
				.localStatus(HOLD_PLACED)
				.build());
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtPickupAgency({})", parameters);

		final var transactionId = UUID.randomUUID().toString();

		return constructPickupTransactionRequest(parameters, transactionId)
			.flatMap(this::createTransaction)
			.map(response -> LocalRequest.builder()
				.localId(transactionId)
				.localStatus(HOLD_PLACED)
				.build());
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtLocalAgency({})", parameters);

		final var transactionId = UUID.randomUUID().toString();

		return constructSelfBorrowingTransactionRequest(parameters, transactionId)
			.flatMap(this::createTransaction)
			.map(response -> LocalRequest.builder()
				.localId(transactionId)
				.localStatus(HOLD_PLACED)
				.rawLocalStatus(getValueOrNull(response, CreateTransactionResponse::getStatus))
				.build());
	}

	// https://folio-org.atlassian.net/wiki/spaces/FOLIJET/pages/1406021/DCB+Borrowing_PickUp+Flow+Details
	private Mono<MutableHttpRequest<CreateTransactionRequest>> constructBorrowing_PickupTransactionRequest(
		PlaceHoldRequestParameters parameters, String transactionId) {
		log.debug("constructBorrowing_PickupTransactionRequest({})", parameters);

		assertExtendedBorrowingRequestParameters(parameters);

		final var itemId = dnsUUID(
			parameters.getSupplyingAgencyCode() + ":" + parameters.getSupplyingLocalItemId())
			.toString();

		final var firstPatronBarcodeInList = parseList(parameters.getLocalPatronBarcode()).get(0);

		final var pickupLocation = resolvePickupLocation(parameters);
		return Mono.zip(
			findLocalItemType(parameters.getCanonicalItemType()),
			consortiumService.isEnabled(VIRTUAL_PATRON_NAMES_VISIBLE)
		).map(tuple -> {
			final var localItemType = tuple.getT1();
			final var namesVisible = tuple.getT2();
			var patronBuilder = CreateTransactionRequest.Patron.builder()
				.id(parameters.getLocalPatronId())
				.barcode(firstPatronBarcodeInList);
			if (namesVisible) {
				patronBuilder.localNames(parameters.getLocalNames());
			}
			return authorisedRequest(POST, "/dcbService/transactions/" + transactionId)
				.body(CreateTransactionRequest.builder()
					.role("BORROWING-PICKUP")
					.item(CreateTransactionRequest.Item.builder()
						.id(itemId)
						.title(parameters.getTitle())
						.barcode(parameters.getSupplyingLocalItemBarcode())
						.materialType(localItemType)
						.lendingLibraryCode(parameters.getSupplyingAgencyCode())
						.build())
					.patron(patronBuilder.build())
					.pickup(CreateTransactionRequest.Pickup.builder()
						.servicePointId(pickupLocation)
						.build())
					.build());
		});
	}

	// https://folio-org.atlassian.net/browse/UXPROD-5114
	private Mono<MutableHttpRequest<CreateTransactionRequest>> constructSelfBorrowingTransactionRequest(
		PlaceHoldRequestParameters parameters, String transactionId) {

		final var patronId = getRequiredParameter("localPatronId",
			parameters, PlaceHoldRequestParameters::getLocalPatronId);

		final var patronBarcodes = getRequiredParameter("localPatronBarcode",
			parameters, PlaceHoldRequestParameters::getLocalPatronBarcode);

		final var firstPatronBarcode = parseList(patronBarcodes).get(0);

		final var pickupLocationLocalId = getRequiredParameter("pickupLocation.localId",
			parameters, PlaceHoldRequestParameters::getPickupLocation, Location::getLocalId);

		final var itemId = getRequiredParameter("supplyingLocalItemId",
			parameters, PlaceHoldRequestParameters::getSupplyingLocalItemId);

		final var itemBarcode = getRequiredParameter("supplyingLocalItemBarcode",
			parameters, PlaceHoldRequestParameters::getSupplyingLocalItemBarcode);

		return Mono.just(authorisedRequest(POST, "/dcbService/transactions/" + transactionId)
				.body(CreateTransactionRequest.builder()
					.role("BORROWING-PICKUP")
					.selfBorrowing(true)
					.item(CreateTransactionRequest.Item.builder()
						.id(itemId)
						.barcode(itemBarcode)
						.build())
					.patron(CreateTransactionRequest.Patron.builder()
						.id(patronId)
						.barcode(firstPatronBarcode)
						.build())
					.pickup(CreateTransactionRequest.Pickup.builder()
						.servicePointId(pickupLocationLocalId)
						.build())
					.build()));
	}

	// https://folio-org.atlassian.net/wiki/spaces/FOLIJET/pages/1406564/DCB+Borrowing+Flow+Details
	private Mono<MutableHttpRequest<CreateTransactionRequest>> constructBorrowingTransactionRequest(
		PlaceHoldRequestParameters parameters, String transactionId) {

		final var itemId = dnsUUID(
			parameters.getSupplyingAgencyCode() + ":" + parameters.getSupplyingLocalItemId())
			.toString();

		final var agencyCode = getValueOrNull(parameters.getPickupAgency(), Agency::getCode);
		final var firstBarcodeInList = parseList(parameters.getLocalPatronBarcode()).get(0);
		final var libraryCode = getValueOrNull(parameters.getPickupLibrary(), Library::getAbbreviatedName);
		final var servicePointName = getValueOrNull(parameters.getPickupLocation(), Location::getPrintLabel);

		return findLocalItemType(parameters.getCanonicalItemType())
			.map(localItemType -> authorisedRequest(POST, "/dcbService/transactions/" + transactionId)
				.body(CreateTransactionRequest.builder()
					.role("BORROWER")
					.item(CreateTransactionRequest.Item.builder()
						.id(itemId)
						.title(parameters.getTitle())
						.barcode(parameters.getSupplyingLocalItemBarcode())
						.materialType(localItemType)
						.build())
					.patron(CreateTransactionRequest.Patron.builder()
						.id(parameters.getLocalPatronId())
						.barcode(firstBarcodeInList)
						.build())
					.pickup(CreateTransactionRequest.Pickup.builder()
						.servicePointId(dnsUUID("FolioServicePoint:" + agencyCode).toString())
						.servicePointName(servicePointName)
						.libraryCode(libraryCode)
						.build())
					.build()));
	}

	// https://folio-org.atlassian.net/wiki/spaces/FOLIJET/pages/1406357/DCB+Pickup+Flow+details
	private Mono<MutableHttpRequest<CreateTransactionRequest>> constructPickupTransactionRequest(
		PlaceHoldRequestParameters parameters, String transactionId) {

		final var itemId = dnsUUID(
			parameters.getSupplyingAgencyCode() + ":" + parameters.getSupplyingLocalItemId())
			.toString();

		final var firstPatronBarcodeInList = parseList(parameters.getLocalPatronBarcode()).get(0);

		final var pickupLocation = resolvePickupLocation(parameters);

		// We must find the local item type AND check the virtual names setting. Zip time
		return Mono.zip(
			findLocalItemType(parameters.getCanonicalItemType()),
			consortiumService.isEnabled(VIRTUAL_PATRON_NAMES_VISIBLE)
		).map(tuple -> {
			final var localItemType = tuple.getT1();
			final var namesVisible = tuple.getT2();
			var patronBuilder = CreateTransactionRequest.Patron.builder()
				.id(parameters.getLocalPatronId())
				.barcode(firstPatronBarcodeInList)
				.group(parameters.getLocalPatronType());

			if (namesVisible) {
				patronBuilder.localNames(parameters.getLocalNames());
			}
			return authorisedRequest(POST, "/dcbService/transactions/" + transactionId)
				.body(CreateTransactionRequest.builder()
					.role("PICKUP")
					.item(CreateTransactionRequest.Item.builder()
						.id(itemId)
						.title(parameters.getTitle())
						.barcode(parameters.getSupplyingLocalItemBarcode())
						.materialType(localItemType)
						.lendingLibraryCode(parameters.getSupplyingAgencyCode())
						.build())
					.patron(patronBuilder.build())
					.pickup(CreateTransactionRequest.Pickup.builder()
						.servicePointId(pickupLocation)
						.build())
					.build());
		});
	}

	private static String resolvePickupLocation(PlaceHoldRequestParameters parameters) {

		if (parameters.getPickupLocation() != null &&
			parameters.getPickupLocation().getLocalId() != null) {

			final var pickup_location = parameters.getPickupLocation().getLocalId();

			log.debug("Overriding pickup location code with ID from selected record: {}", pickup_location);
			return pickup_location;
		}

		throw new IllegalArgumentException("Pickup locations local id missing.");
	}

	private void assertExtendedBorrowingRequestParameters(PlaceHoldRequestParameters parameters) {
		requiredParameter(parameters.getTitle(), "Title");
		requiredParameter(parameters.getCanonicalItemType(), "Canonical item type");
		requiredParameter(parameters.getSupplyingAgencyCode(), "Supplying agency code");
		requiredParameter(parameters.getSupplyingLocalItemId(), "Supplying local item id");
		requiredParameter(parameters.getSupplyingLocalItemBarcode(), "Supplying local item barcode");
	}

	private <T> String getRequiredParameter(String parameterName, T parameters, Function<T, String> accessor) {
		return getRequiredParameter(parameterName, parameters, accessor, identity());
	}

	private <T, S> String getRequiredParameter(String parameterName, T parameters,
		Function<T, S> accessor, Function<S, String> mapping) {

		final var value = getValueOrNull(parameters, accessor, mapping);

		requiredParameter(value, parameterName);

		return value;
	}

	private void requiredParameter(String value, String parameterName) {
		if (isNullOrEmpty(value)) {
			throw new MissingParameterException(parameterName);
		}
	}

	public Mono<String> findLocalItemType(String canonicalItemType) {
		if (canonicalItemType == null) {
			return Mono.empty();
		}

		// Because mappings from host systems TO canonical types are M:1 we can't use reciprocal mappings, so always
		// look for an explict DCB:ItemType:CIRC -> FOLIO1234:ItemType:book mapping
		return referenceValueMappingService.findMapping("ItemType", "DCB", canonicalItemType, "ItemType", getHostLmsCode())
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.error(new NoItemTypeMappingFoundException(
				"Unable to map canonical item type \"" + canonicalItemType + "\" to a item type on Host LMS: \"" + getHostLmsCode() + "\"",
				getHostLmsCode(), canonicalItemType)));
	}

	@Override
	public Mono<String> findLocalPatronType(String canonicalPatronType) {
		if (canonicalPatronType == null) {
			return Mono.empty();
		}

		return referenceValueMappingService.findMapping("patronType", "DCB", canonicalPatronType, "patronType", getHostLmsCode())
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.error(new NoPatronTypeMappingFoundException(
				"Unable to map canonical patron type \"" + canonicalPatronType + "\" to a patron type on Host LMS: \"" + getHostLmsCode() + "\"",
				getHostLmsCode(), canonicalPatronType)));
	}

	@Override
	public Mono<String> findCanonicalPatronType(String localPatronType, String localId) {
		String hostLmsCode = getHostLmsCode();
		if (localPatronType == null) {
			return Mono.empty();
		}

		return referenceValueMappingService.findMapping("patronType",
				hostLmsCode, localPatronType, "patronType", "DCB")
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.error(new NoPatronTypeMappingFoundException(
				"Unable to map patron type \"" + localPatronType + "\" on Host LMS: \"" + hostLmsCode + "\" to canonical value",
				hostLmsCode, localPatronType)));
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		final var query = exactEqualityQuery("id", localPatronId);

		return findUsers(query)
			.flatMap(response -> mapFirstUserToPatron(response, query, Mono.empty()))
			.doOnError(error -> log.error("Error occurred while fetching patron by local id: {}", localPatronId, error));
	}

	@Override
	public Mono<Patron> getPatronByIdentifier(String id) {
		return getPatronByLocalId(id)
			.switchIfEmpty(Mono.error(patronNotFound(id, getHostLmsCode())));
	}

	@Override
	public Mono<Patron> getPatronByUsername(String localUsername) {
		// we use barcode for patron lookup not username
		return findUserByBarcode(localUsername)
			.doOnError(error -> log.error("Error occurred while fetching patron by username: {}", localUsername, error));
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		try {
			final var barcodeListString = getValueOrNull(patron,
				org.olf.dcb.core.model.Patron::determineHomeIdentityBarcode);

			final var firstBarcodeInList = parseList(barcodeListString).get(0);

			log.debug("Finding virtual patron using barcode: {} from barcode list: {}.",
				firstBarcodeInList, barcodeListString);

			return findUsersWithReturnValues(firstBarcodeInList);
		} catch (NoHomeIdentityException | NoHomeBarcodeException e) {
			return Mono.error(FailedToFindVirtualPatronException.noBarcode(
				getValueOrNull(patron, org.olf.dcb.core.model.Patron::getId)));
		}
	}

	private Mono<Patron> findUsersWithReturnValues(String barcode) {

		final var query = exactEqualityQuery("barcode", barcode);

		return findUsers(query)
			.flatMap(response -> mapFirstUserToPatron(response, query, Mono.empty())
				.switchIfEmpty(raiseError(VirtualPatronNotFound.builder()
					.with("barcode", barcode)
					.with("query", query)
					.with("Response", response)
					.build()))
				.onErrorResume(MultipleUsersFoundException.class, error -> raiseError(
					MultipleVirtualPatronsFound.builder()
						.withDetail(error.getMessage())
						.with("barcode", barcode)
						.with("Response", response)
						.build())
				)
			);
	}

	private Mono<Patron> findUserByQuery(String identifier, String id) {
		final var query = exactEqualityQuery(identifier, id);

		return findUsers(query)
			.flatMap(response -> mapFirstUserToPatron(response, query, Mono.empty()));
	}

	private Mono<Patron> findUserByBarcode(String barcode) {
		final var query = exactEqualityQuery("barcode", barcode);

		return findUsers(query)
			.flatMap(response -> mapFirstUserToPatron(response, query, Mono.empty()));
	}

	private Mono<UserCollection> findUsers(CqlQuery query) {
		// Duplication in path due to way edge-users is namespaced
		final var request = authorisedRequest(GET, "/users/users")
			.uri(uriBuilder -> uriBuilder.queryParam("query", query));

		return makeRequest(request, Argument.of(UserCollection.class));
	}

	private Mono<Patron> mapFirstUserToPatron(UserCollection response,
		CqlQuery query, Mono<Patron> emptyReturnValue) {

		final var users = getValueOrNull(response, UserCollection::getUsers);

		if (isEmpty(users)) {
			return emptyReturnValue;
		}

		if (users.size() > 1) {
			return Mono.error(new MultipleUsersFoundException(query, getHostLmsCode()));
		}

		return Mono.just(users.stream().findFirst().orElseThrow())
			.flatMap(this::mapUserToPatron);
	}

	private Mono<Patron> mapUserToPatron(User user) {
		return Mono.justOrEmpty(conversionService.convert(user, Patron.class).orElse(null))
			.flatMap(this::enrichWithCanonicalPatronType);
	}

	private Mono<Patron> enrichWithCanonicalPatronType(Patron incomingPatron) {
		return Mono.just(incomingPatron)
			.zipWhen(this::findPatronType, Patron::setCanonicalPatronType)
			.defaultIfEmpty(incomingPatron);
	}

	private Mono<String> findPatronType(Patron patron) {
		return findCanonicalPatronType(patron.getLocalPatronType(), null);
	}

	@Override
	public Mono<String> createPatron(Patron patron) {
		// edge-dcb creates the virtual patron on DCB's behalf when creating the transaction
		// DCB has no means to do this via other / alternative edge modules
		// hence needs to trigger this mechanism by generating a new ID
		// (that should not already be present in the FOLIO tenant)
		return Mono.just(UUID.randomUUID().toString());
	}

	/**
	 * Not implemented.
	 * A bib creation is not needed to complete the Borrowing_PickUp Flow.
	 *
	 * @see <a href="https://wiki.folio.org/display/FOLIJET/DCB+Borrowing_PickUp+Flow+Details">
	 *      DCB Borrowing PickUp Flow Details</a>
	 *
	 * @return A Mono emitting a String representing of the unique identifier,
	 *         used to signify the completion of the bib creation process in the
	 *         DCB workflow.
	 */
	@Override
	public Mono<String> createBib(Bib bib) {
		return Mono.just( UUID.randomUUID().toString() );
	}

	@Override
	public Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters) {
			log.debug("{} cancelHoldRequest({})", getHostLms().getName(), parameters);
			return updateTransactionStatus(parameters.getLocalRequestId(), TransactionStatus.CANCELLED)
				.thenReturn(parameters.getLocalRequestId());
		}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal hostLmsRenewal) {
		final var transactionId = getValueOrThrow(hostLmsRenewal,
			HostLmsRenewal::getLocalRequestId, () -> new RuntimeException("Cannot renew transaction without a transaction ID"));

		final var path = "/dcbService/transactions/%s/renew".formatted(transactionId);

		return makeRequest(authorisedRequest(PUT, path))
			.then(Mono.just(hostLmsRenewal));

	}

	@Override
	@SneakyThrows
	public Mono<LocalRequest> updateHoldRequest(LocalRequest localRequest) {
		final var localRequestId = getValueOrThrow(localRequest, LocalRequest::getLocalId,
			() -> new MissingParameterException("local ID"));

		final var requestedItemBarcode = getValueOrThrow(localRequest,
			LocalRequest::getRequestedItemBarcode, () -> new MissingParameterException("requested item barcode"));

		final var canonicalItemType = getValueOrThrow(localRequest, LocalRequest::getCanonicalItemType,
			() -> new MissingParameterException("canonical item type"));

		final var supplyingAgencyCode = getValueOrThrow(localRequest, LocalRequest::getSupplyingAgencyCode,
			() -> new MissingParameterException("supplying agency code"));

		final var path = "/dcbService/transactions/%s".formatted(localRequestId);

		return findLocalItemType(canonicalItemType)
			.map(localItemType -> authorisedRequest(PUT, path)
			.body(UpdateTransactionRequest.builder()
				.item(UpdateTransactionRequest.Item.builder()
					.barcode(requestedItemBarcode)
					.materialType(localItemType)
					.lendingLibraryCode(supplyingAgencyCode)
					.build())
				.build()))
			.flatMap(this::makeRequest)
			.thenReturn(localRequest);
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		// DCB has no means to update users via the available edge modules
		// and edge-dcb does not do this on DCB's behalf when creating the transaction
		log.warn("NOOP: updatePatron called for hostlms {} localPatronId {} localPatronType {}",
			getHostLms().getName(), localId, patronType);

		// Finding the existing user by ID is the easiest way to support the current
		// host LMS client interface
		final var query = exactEqualityQuery("id", localId);

		return findUsers(query)
			.flatMap(response -> mapFirstUserToPatron(response, query,
				Mono.error(FailedToFindVirtualPatronException.notFound(localId, getHostLmsCode()))))
			.doOnSuccess(patron -> log.warn("NOOP: updatePatron for hostlms {} returning {}",
				getHostLms().getName(), patron));
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String barcode, String secret) {

		if (!isValidAuthProfile(authProfile)) {
			return Mono.error(() -> new HttpStatusException(BAD_REQUEST, "Invalid authProfile: " + authProfile));
		}

		return findPatronByBarcode(barcode)
			.flatMap(patron -> verifyPatronPin(patron, secret))
			.doOnError(error -> log.error("Error occurred while handling patron authentication: {}", barcode, error));
	}

	private Mono<Patron> findPatronByBarcode(String barcode) {
		return findUserByBarcode(barcode);
	}

	private Mono<Patron> verifyPatronPin(Patron patron, String pin) {
		final var localID = patron.getLocalId().stream().findFirst().orElseThrow();
		final var request = authorisedRequest(POST, "/users/patron-pin/verify")
			.body(VerifyPatron.builder().id(localID).pin(pin).build());

		return makeRequest(request, VOID)
			.thenReturn(patron);
	}

	private Boolean isValidAuthProfile(String authProfile) {
		return authProfile.equals("BASIC/BARCODE+PIN");
	}

	/**
	 * Not implemented.
	 * The item will be created as part of the Borrowing_PickUp Flow.
	 *
	 * @see <a href="https://wiki.folio.org/display/FOLIJET/DCB+Borrowing_PickUp+Flow+Details">
	 *      DCB Borrowing PickUp Flow Details</a>
	 *
	 * @return A Mono emitting the created HostLmsItem, used to signify the completion
	 *         of the item creation process in the DCB workflow.
	 */
	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		return Mono.just(HostLmsItem.builder().build());
	}

	@Override
	public Mono<HostLmsRequest> getRequest(HostLmsRequest request) {
		final var localRequestId = getValueOrNull(request, HostLmsRequest::getLocalId);

		return getTransactionStatus(localRequestId)
			.map(transactionStatus -> mapToHostLmsRequest(localRequestId, transactionStatus))
			.onErrorResume(TransactionNotFoundException.class,
				t -> Mono.just(missingHostLmsRequest(localRequestId)));
	}

	private static HostLmsRequest mapToHostLmsRequest(String transactionId,
		TransactionStatus transactionStatus) {

		final var status = getValueOrNull(transactionStatus, TransactionStatus::getStatus);
		final Integer holdCount = determineHoldCount(transactionStatus);

		// Based upon the statuses defined in https://github.com/folio-org/mod-dcb/blob/master/src/main/resources/swagger.api/schemas/transactionStatus.yaml
		final var mappedStatus = switch(status) {
			case "CREATED" -> HOLD_CONFIRMED;
			case "CANCELLED" -> HOLD_CANCELLED;
			// Some statuses are expected but do not map to any request status
			// Due to mod-dcb using a single transaction status to represent both
			// Pass both recognised but unhandled and unrecognised statuses without mapping
			default -> status;
		};

		return HostLmsRequest.builder()
			.localId(transactionId)
			.status(mappedStatus)
			.rawStatus(status)
			.requestedItemHoldCount(holdCount)
			.build();
	}

	private static HostLmsRequest missingHostLmsRequest(String localRequestId) {
		return HostLmsRequest.builder()
			.localId(localRequestId)
			.status("MISSING")
			.build();
	}

	@Override
	public Mono<HostLmsItem> getItem(HostLmsItem hostLmsItem) {
		log.debug("getItem({})", hostLmsItem);

		final var localItemId = getValueOrNull(hostLmsItem, HostLmsItem::getLocalId);
		final var localRequestId = getValueOrNull(hostLmsItem, HostLmsItem::getLocalRequestId);
		final var currentHoldCount = hostLmsItem.getHoldCount();

		return getTransactionStatus(localRequestId)
			.doOnSuccess(transactionStatus -> log.debug("got transaction {} status {}",  localRequestId, transactionStatus))
			.map(transactionStatus -> mapToHostLmsItem(localItemId, transactionStatus, currentHoldCount))
			.onErrorResume(TransactionNotFoundException.class, t -> missingHostLmsItem(localItemId));
	}

	private static int determineHoldCount(TransactionStatus transactionStatus) {
		// In some situations, mod-dcb can throw us either a null item or a null transaction status
		// This check and the one in getHoldCount should take care of it
		if (transactionStatus == null) {
			return 0;
		}
		return transactionStatus.getHoldCount();
	}

	private static HostLmsItem mapToHostLmsItem(String itemId,
		TransactionStatus transactionStatus, Integer existingHoldCount) {

		if (itemId == null) {
			log.warn("getItem returning HostLmsItem with a null item id.");
		}

		final var rawLocalStatus = getValueOrNull(transactionStatus, TransactionStatus::getStatus);

		int finalHoldCount = determineHoldCount(transactionStatus);

		// In FOLIO, there is an odd disparity between what is reported in mod-dcb and what is reported from RTAC
		// This block exists to check that, by comparing and verifying what is being returned.
		// To ensure we do not miss a hold (DCB-2123)
		if (finalHoldCount == 0 && existingHoldCount != null && existingHoldCount > 0) {
			log.debug("mod-dcb reported 0 holds for item {}, falling back to count from holdings: {}",
				itemId, existingHoldCount);
			finalHoldCount = existingHoldCount;
		}

		return HostLmsItem.builder()
			.localId(itemId)
			.status(mapToItemStatus(rawLocalStatus))
			.rawStatus(rawLocalStatus)
			.renewalCount(getValue(transactionStatus, TransactionStatus::getRenewalCount, 0))
			.holdCount(finalHoldCount)
			.renewable(transactionStatus.getRenewable())
			.build();
	}

	private static String mapToItemStatus(String transactionStatus) {
		// Based upon the statuses defined in https://github.com/folio-org/mod-dcb/blob/master/src/main/resources/swagger.api/schemas/transactionStatus.yaml
		return switch (transactionStatus) {
			// When the item is returned back to the supplying agency, the transaction is closed
			// The Host LMS reactions use the available local item status to represent the item becoming available again at the end
			case "CLOSED" -> ITEM_AVAILABLE;
			case "AWAITING_PICKUP" -> ITEM_ON_HOLDSHELF;
			case "ITEM_CHECKED_OUT" -> ITEM_LOANED;
			// OPEN is considered a trigger for pickup transit
			case "OPEN",
				// This is needed to trigger the return to the lending agency workflow action
				"ITEM_CHECKED_IN" -> ITEM_TRANSIT;
			// Some statuses are expected but do not map to any item status
			// Due to mod-dcb using a single transaction status to represent both
			// Pass both recognised but unhandled and unrecognised statuses without mapping
			default -> transactionStatus;
		};
	}

	private static Mono<HostLmsItem> missingHostLmsItem(String itemId) {
		return Mono.just(HostLmsItem.builder()
			.localId(itemId)
			.status("MISSING")
			.build());
	}

	private Mono<TransactionStatus> getTransactionStatus(String localRequestId) {
		// Should not attempt to get transaction status when no ID is provided
		if (isEmpty(localRequestId)) {
			return Mono.error(
				new NullPointerException("Cannot use transaction id: "+localRequestId+" to fetch transaction status."));
		}

		final var path = "/dcbService/transactions/%s/status".formatted(localRequestId);

		return makeRequest(authorisedRequest(GET, path), Argument.of(TransactionStatus.class),
			response -> response.
				onErrorMap(HttpResponsePredicates::isNotFound, t -> new TransactionNotFoundException()));
	}

	private Mono<TransactionStatus> updateTransactionStatus(String localRequestId, String status) {
		final var path = "/dcbService/transactions/%s/status".formatted(localRequestId);

		return makeRequest(authorisedRequest(PUT, path)
				.body(TransactionStatus.builder()
					.status(status)
					.build()),
			Argument.of(TransactionStatus.class));
	}

	@Override
	public Mono<String> updateItemStatus(HostLmsItem hostLmsItem, CanonicalItemState crs) {

		final var localRequestId = getValueOrNull(hostLmsItem, HostLmsItem::getLocalRequestId);
		final var toStatus = mapToTransactionStatus(crs);

		// Don't send a request if we don't have a crs translation
		if (toStatus.equals("OK")) {
			// Still progress the workflow
			return Mono.just(toStatus);
		}

		return updateTransactionStatus(localRequestId, toStatus)
			.thenReturn("OK")
			;
	}

	private static String mapToTransactionStatus(CanonicalItemState crs) {
		return switch (crs) {
			// HandleSupplierInTransit
			case TRANSIT -> TransactionStatus.OPEN;

			// HandleBorrowerItemOnHoldShelf
			case RECEIVED -> TransactionStatus.AWAITING_PICKUP;

			// HandleSupplierItemAvailable
			case COMPLETED -> TransactionStatus.CLOSED;

			// HandleBorrowerItemLoaned
			case AVAILABLE,

				// HandleBorrowerItemAvailable
				OFFSITE,

				// not implemented in DCB
				MISSING, ONHOLDSHELF -> unimplementedTransactionStatusMapping(crs);
		};
	}

	private static String unimplementedTransactionStatusMapping(CanonicalItemState crs) {
		log.warn("Update item status requested for {} and we don't have a folio translation for that", crs);

		return "OK";
	}

	@Override
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkout) {

		final var localRequestId = getValueOrNull(checkout, CheckoutItemCommand::getLocalRequestId);

		if (localRequestId == null) {
			return Mono.error(
				new NullPointerException("cannot use null localRequestId to update transaction status"));
		}

		final var itemId = getValueOrNull(checkout, CheckoutItemCommand::getItemId);
		final var patronBarcode = getValueOrNull(checkout, CheckoutItemCommand::getPatronBarcode);

		// HandleBorrowerItemLoaned
		return updateTransactionStatus(localRequestId, TransactionStatus.ITEM_CHECKED_OUT)
			.thenReturn("OK")
			.switchIfEmpty(Mono.error(() ->
				new DcbError("Check out of " + itemId + " to " + patronBarcode + " at " + getHostLmsCode() + " failed")));
	}

	@Override
	public Mono<String> deleteItem(DeleteCommand deleteCommand) {
		log.info("Delete virtual item is not currently implemented for FOLIO");
		return Mono.just("OK");
	}

  @Override
  public Mono<String> deleteHold(DeleteCommand deleteCommand) {
		log.info("Delete hold is not currently implemented for FOLIO");
    return Mono.just("OK");
  }

	@Override
	public Mono<String> deleteBib(String id) {
		log.info("Delete virtual bib is not currently implemented for FOLIO");
		return Mono.just("OK");
	}

  public Mono<String> deletePatron(String id) {
    log.info("Delete patron is not currently implemented");
    return Mono.empty();
  }

	/**
	 * Make HTTP request to a FOLIO system
	 *
	 * @param request Request to send
	 * @param responseBodyType Expected type of the response body
	 * @return Deserialized response body or error, that might have been transformed already by handler
	 * @param <TResponse> Type to deserialize the response to
	 * @param <TRequest> Type of the request body
	 */
	public <TResponse, TRequest> Mono<TResponse> makeRequest(
		MutableHttpRequest<TRequest> request, Argument<TResponse> responseBodyType) {

		return makeRequest(request, responseBodyType, noExtraErrorHandling());
	}

	/**
	 * Make HTTP request to a FOLIO system
	 *
	 * @param request Request to send
	 * @param responseBodyType Expected type of the response body
	 * @param errorHandlingTransformer method for handling errors after the response has been received
	 * @return Deserialized response body or error, that might have been transformed already by handler
	 * @param <TResponse> Type to deserialize the response to
	 * @param <TRequest> Type of the request body
	 */
	@Retryable(attempts="1", includes = ResponseClosedException.class)
	public <TResponse, TRequest> Mono<TResponse> makeRequest(
		@NonNull MutableHttpRequest<TRequest> request, @NonNull Argument<TResponse> responseBodyType,
		Function<Mono<TResponse>, Mono<TResponse>> errorHandlingTransformer) {

		log.trace("Making request: {} to Host LMS: {}", toLogOutput(request), getHostLmsCode());

		return Mono.from(httpClient.retrieve(request, responseBodyType))
			.doOnSuccess(this::logResponse)
			.doOnError(HttpClientResponseException.class, this::logErrorResponse)
			.onErrorMap(HttpResponsePredicates::isUnauthorised, InvalidApiKeyException::new)
			.doOnError(InvalidApiKeyException.class, error -> log.error("Invalid API key", error))
			// Additional request specific error handling
			.transform(errorHandlingTransformer)
			// This has to go after more specific error handling
			// as will convert any client response exception to a problem
			.onErrorMap(HttpClientResponseException.class,
				responseException -> toUnexpectedResponseProblem(request, responseException))
			.doOnError(UnexpectedHttpResponseProblem.class, ConsortialFolioHostLmsClient::logErrorResponse);
	}

	/**
	 * Make HTTP request to a FOLIO system, without expecting a response body
	 *
	 * @param request Request to send
	 * @return Mono with no value (as no response body was present) or error
	 * @param <TRequest> Type of the request body
	 */
	@Retryable(attempts="1", includes = ResponseClosedException.class)
	public <TRequest> Mono<Void> makeRequest(@NonNull MutableHttpRequest<TRequest> request) {
		return Mono.from(httpClient.exchange(request))
			.doOnSuccess(this::logResponse)
			.doOnError(HttpClientResponseException.class, this::logErrorResponse)
			.onErrorMap(HttpResponsePredicates::isUnauthorised, InvalidApiKeyException::new)
			.doOnError(InvalidApiKeyException.class, this::logInvalidApiKeyError)
			.onErrorMap(HttpClientResponseException.class, responseException ->
				unexpectedResponseProblem(responseException, request, getHostLmsCode()))
			.doOnError(UnexpectedHttpResponseProblem.class, ConsortialFolioHostLmsClient::logErrorResponse)
			.then();
	}

	/**
	 * Utility method to specify that no specialised error handling will be needed for this request
	 *
	 * @return transformer that provides no additionally error handling
	 * @param <T> Type of response being handled
	 */
	private static <T> Function<Mono<T>, Mono<T>> noExtraErrorHandling() {
		return identity();
	}

	private MutableHttpRequest<Object> authorisedRequest(HttpMethod method, String path) {
		final var relativeUri = UriBuilder.of(path).build();

		return HttpRequest.create(method, resolve(relativeUri).toString())
			// Base 64 encoded API key
			.header("Authorization", apiKey)
			// MUST explicitly accept JSON for edge-rtac otherwise XML will be returned
			// for other edge APIs it's only good form
			.accept(APPLICATION_JSON);
	}

	private URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(rootUri, relativeURI);
	}

	private void logErrorResponse(HttpClientResponseException error) {
		log.debug("Received error response: {} from Host LMS: {}", toLogOutput(error.getResponse()), getHostLmsCode());
	}

	private static void logErrorResponse(UnexpectedHttpResponseProblem error) {
		log.error(error.toString(), error);
	}

	private <T> void logResponse(T response) {
		log.debug("Received response: {} from Host LMS: {}", response, getHostLmsCode());
	}

	private void logInvalidApiKeyError(InvalidApiKeyException error) {
		log.error("Invalid API key for host LMS: {}", getHostLmsCode());
	}

	private <TRequest> ThrowableProblem toUnexpectedResponseProblem(
		MutableHttpRequest<TRequest> request, HttpClientResponseException responseException) {

		return unexpectedResponseProblem(responseException, request, getHostLmsCode());
	}

	public Mono<Boolean> supplierPreflight(String borrowingAgencyCode,
		String supplyingAgencyCode, String canonicalItemType,
		String canonicalPatronType) {

		log.debug("CONSORTIAL FOLIO Supplier Preflight {} {} {} {}",
			borrowingAgencyCode, supplyingAgencyCode, canonicalItemType,
			canonicalPatronType);

		return Mono.just(true);
	}

	private static RuntimeException patronNotFound(String localId, String hostLmsCode) {
		return new PatronNotFoundInHostLmsException(localId, hostLmsCode);
	}

	@Data
	@Builder
	@Serdeable
	static class ValidationError {
		List<Error> errors;

		private String getFirstError() {
			return getErrors().stream()
				.findFirst()
				.map(Error::getMessage)
				.orElse("Unknown validation error");
		}

		@Data
		@Builder
		@Serdeable
		static class Error {
			String message;
			String type;
			String code;
		}
	}

	@Data
	@Builder
	@Serdeable
	static class TransactionUnauthorisedResponse {
		Integer status;
		String error;
		String path;
		String timestamp;
	}

	@Serdeable
	static class NoResponseBody {

	}

	@Override
	public @NonNull String getClientId() {
		
		// Uri "toString" behaviour will sometimes return the string provided at initialization.
		// While this is OK for general operation, we need to compare values here. Resolving a relative URI
		// will force the toString method to construct a new string representation, meaning it's more comparable.
		return rootUri.resolve("/").toString();
	}

  @Override
  public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
		// See https://s3.amazonaws.com/foliodocs/api/mod-circulation/p/circulation.html#circulation_loans__loanid__put
		// Specifcally PUT /circulation/loans/{loanId} and renewalCount property - needs the loan ID
		// WE don't seem to access to loan id :/
    log.info("Folio prevent renewal {}",prc);
		// This is fugly

		// This endpoint was introduced after Sunflower SP3, in its own special deployment
		// This will set the renewal count to maximum
		final var path = "/dcbService/transactions/%s/block-renewal".formatted(prc.getRequestId());
		// If we get 404 it suggests this endpoint isn't supported. Revert to previous behaviour
		return makeRequest(authorisedRequest(PUT, path));
  }

  @Override
  public Mono<PingResponse> ping() {

		Publisher<HttpResponse<Void>> responsePublisher = httpClient.exchange(rootUri+"/_/proxy/health", Void.class);
    Instant start = Instant.now();

		return Mono.from(responsePublisher)
			.onErrorResume ( e -> {
				log.error("Problem pinging host {}",e.getMessage());
				return Mono.empty();
			})
			.flatMap(response -> {
				if (response.getStatus().getCode() == 200) {
					return Mono.just(PingResponse.builder()
			      .target(getHostLmsCode())
			      .status("OK")
	          .pingTime(Duration.between(start, Instant.now()))
			      .build());
				} else {
					return Mono.just(PingResponse.builder()
			      .target(getHostLmsCode())
			      .status("ERROR")
	          .pingTime(Duration.between(start, Instant.now()))
			      .build());
				}
			});
  }

  public String getHostLmsCode() {
    String result = hostLms.getCode();
    if ( result == null ) {
      log.warn("getCode from hostLms returned NULL : {}",hostLms);
    }
    return result;
  }

}

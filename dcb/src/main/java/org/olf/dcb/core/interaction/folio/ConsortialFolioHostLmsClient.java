package org.olf.dcb.core.interaction.folio;

import static io.micronaut.core.type.Argument.VOID;
import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static io.micronaut.http.HttpMethod.GET;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.HttpStatus.*;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static org.olf.dcb.core.interaction.folio.CqlQuery.exactEqualityQuery;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.dcb.utils.CollectionUtils.nonNullValuesList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.exceptions.HttpStatusException;
import org.apache.commons.lang3.NotImplementedException;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.FailedToGetItemsException;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsHold;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.HttpResponsePredicates;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.RelativeUriResolver;
import org.olf.dcb.core.interaction.folio.User.PersonalDetails;
import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.NoHomeBarcodeException;
import org.olf.dcb.core.model.NoHomeIdentityException;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.request.fulfilment.PatronTypeService;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
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

	private final ItemStatusMapper itemStatusMapper;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService;
	private final PatronTypeService patronTypeService;

	private final String apiKey;
	private final URI rootUri;

	public static ItemStatusMapper.FallbackMapper folioFallback() {
		final var statusCodeMap = Map.of(
			"Available", AVAILABLE,
			"Checked out", CHECKED_OUT);

		return statusCode -> isEmpty(statusCode)
			? UNKNOWN
			: statusCodeMap.getOrDefault(statusCode, UNAVAILABLE);
	}

	public ConsortialFolioHostLmsClient(@Parameter HostLms hostLms,
		@Parameter("client") HttpClient httpClient, ItemStatusMapper itemStatusMapper,
		LocationToAgencyMappingService locationToAgencyMappingService,
		MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService,
		PatronTypeService patronTypeService) {

		this.hostLms = hostLms;
		this.httpClient = httpClient;

		this.itemStatusMapper = itemStatusMapper;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.materialTypeToItemTypeMappingService = materialTypeToItemTypeMappingService;
		this.patronTypeService = patronTypeService;

		this.apiKey = API_KEY_SETTING.getRequiredConfigValue(hostLms);
		this.rootUri = UriBuilder.of(BASE_URL_SETTING.getRequiredConfigValue(hostLms)).build();
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
		if (isEmpty(outerHoldings.getHoldings())) {
			return Flux.empty();
		}

		return Flux.fromStream(outerHoldings.getHoldings().stream())
			// When RTAC encounters a holdings record without any items
			// it includes the holdings record in the response
			// DCB is only interested in items and thus needs to differentiate between
			// holdings in the response that are items and those that are only holdings
			// For more information on the flow inside RTAC - https://github.com/folio-org/mod-rtac/blob/3e7f25445ff79b60690fa2025f3a426d9e57fd21/src/main/java/org/folio/mappers/FolioToRtacMapper.java#L112
			.filter(holdings -> itemStatuses.contains(holdings.getStatus()))
			.flatMap(holding -> mapHoldingToItem(holding, outerHoldings.getInstanceId()));
	}

	private Mono<Item> mapHoldingToItem(Holding holding, String instanceId) {
		return Mono.justOrEmpty(holding.getStatus())
			.flatMap(status -> itemStatusMapper.mapStatus(status, hostLms.getCode(), folioFallback()))
			.map(status -> Item.builder()
				.localId(holding.getId())
				.localBibId(instanceId)
				.barcode(holding.getBarcode())
				.callNumber(holding.getCallNumber())
				.status(status)
				.dueDate(holding.getDueDate())
				.holdCount(holding.getTotalHoldRequests())
				.localItemType(getValue(holding.getMaterialType(), MaterialType::getName))
				.localItemTypeCode(getValue(holding.getMaterialType(), MaterialType::getName))
				.location(Location.builder()
					.name(holding.getLocation())
					.code(holding.getLocationCode())
					.build())
				.suppressed(holding.getSuppressFromDiscovery())
				.deleted(false)
				.hostLmsCode(getHostLmsCode())
				.build())
			.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, getHostLmsCode()))
			.flatMap(item -> materialTypeToItemTypeMappingService.enrichItemWithMappedItemType(item, getHostLmsCode()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(
		PlaceHoldRequestParameters parameters) {

		return Mono.error(new NotImplementedException("Placing hold request at supplying agency is not currently implemented for FOLIO"));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(
		PlaceHoldRequestParameters parameters) {

		return Mono.error(new NotImplementedException("Placing hold request at borrowing agency is not currently implemented for FOLIO"));
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		final var query = exactEqualityQuery("id", localPatronId);

		return findUsers(query)
			.flatMap(response -> mapFirstUserToPatron(response, query, Mono.empty()))
			.doOnError(error -> log.error("Error occurred while fetching patron by local id: {}", localPatronId, error));
	}

	@Override
	public Mono<Patron> getPatronByUsername(String localUsername) {
		final var query = exactEqualityQuery("username", localUsername);

			return findUsers(query)
				.flatMap(response -> mapFirstUserToPatron(response, query, Mono.empty()))
				.doOnError(error -> log.error("Error occurred while fetching patron by username: {}", localUsername, error));
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		try {
			final var barcode = getValue(patron, org.olf.dcb.core.model.Patron::determineHomeIdentityBarcode);

			final var query = exactEqualityQuery("barcode", barcode);

			return findUsers(query)
				.flatMap(response -> mapFirstUserToPatron(response, query, Mono.empty()));
		} catch (NoHomeIdentityException | NoHomeBarcodeException e) {
			return Mono.error(FailedToFindVirtualPatronException.noBarcode(
				getValue(patron, org.olf.dcb.core.model.Patron::getId)));
		}
	}

	private Mono<UserCollection> findUsers(CqlQuery query) {
		// Duplication in path due to way edge-users is namespaced
		final var request = authorisedRequest(GET, "/users/users")
			.uri(uriBuilder -> uriBuilder.queryParam("query", query));

		return makeRequest(request, Argument.of(UserCollection.class))
			.onErrorMap(HttpResponsePredicates::isUnauthorised, InvalidApiKeyException::new);
	}

	private Mono<Patron> mapFirstUserToPatron(UserCollection response,
		CqlQuery query, Mono<Patron> emptyReturnValue) {

		final var users = getValue(response, UserCollection::getUsers);

		if (isEmpty(users)) {
			return emptyReturnValue;
		}

		if (users.size() > 1) {
			return Mono.error(new MultipleUsersFoundException(query, getHostLmsCode()));
		}

		return Mono.just(users.stream().findFirst().orElseThrow())
			.flatMap(this::mapUserToPatron);
	}

	private Mono<Patron> mapUserToPatron(@NonNull User user) {
		final var personalDetails = user.getPersonal();

		return Mono.just(Patron.builder()
			.localId(nonNullValuesList(user.getId()))
			.localPatronType(user.getPatronGroup())
			.localBarcodes(nonNullValuesList(user.getBarcode()))
			.localNames(nonNullValuesList(
				getValue(personalDetails, PersonalDetails::getFirstName),
				getValue(personalDetails, PersonalDetails::getMiddleName),
				getValue(personalDetails, PersonalDetails::getLastName)
			))
			.build())
			.flatMap(this::enrichWithCanonicalPatronType);
	}

	private Mono<Patron> enrichWithCanonicalPatronType(Patron incomingPatron) {
		return Mono.just(incomingPatron)
			.zipWhen(this::findPatronType, Patron::setCanonicalPatronType)
			.defaultIfEmpty(incomingPatron);
	}

	private Mono<String> findPatronType(Patron patron) {
		return patronTypeService.findCanonicalPatronType(getHostLmsCode(), patron.getLocalPatronType());
	}

	@Override
	public Mono<String> createPatron(Patron patron) {
		// edge-dcb creates the virtual patron on DCB's behalf when creating the transaction
		// DCB has no means to do this via other / alternative edge modules
		// hence needs to trigger this mechanism by generating a new ID
		// (that should not already be present in the FOLIO tenant)
		return Mono.just(UUID.randomUUID().toString());
	}

	@Override
	public Mono<String> createBib(Bib bib) {
		return Mono.error(new NotImplementedException("Creating virtual bib is not currently implemented for FOLIO"));
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		// DCB has no means to update users via the available edge modules
		// and edge-dcb does not do this on DCB's behalf when creating the transaction

		// Finding the existing user by ID is the easiest way to support the current
		// host LMS client interface
		final var query = exactEqualityQuery("id", localId);

		return findUsers(query)
			.flatMap(response -> mapFirstUserToPatron(response, query,
				Mono.error(FailedToFindVirtualPatronException.notFound(localId, getHostLmsCode()))));
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
		final var query = exactEqualityQuery("barcode", barcode);
		return findUsers(query).flatMap(collection -> mapFirstUserToPatron(collection, query, Mono.empty()));
	}

	private Mono<Patron> verifyPatronPin(Patron patron, String pin) {
		final var localID = patron.getLocalId().stream().findFirst().orElseThrow();
		final var request = authorisedRequest(POST, "/users/patron-pin/verify")
			.body(VerifyPatron.builder().id(localID).pin(pin).build());

		return makeRequest(request, VOID).thenReturn(patron);
	}

	private Boolean isValidAuthProfile(String authProfile) {
		return authProfile.equals("BASIC/BARCODE+PIN");
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		return Mono.error(new NotImplementedException("Creating virtual item is not currently implemented for FOLIO"));
	}

	@Override
	public Mono<HostLmsHold> getHold(String holdId) {
		return Mono.error(new NotImplementedException("Getting hold request is not currently implemented for FOLIO"));
	}

	@Override
	public Mono<HostLmsItem> getItem(String itemId) {
		return Mono.error(new NotImplementedException("Getting item is not currently implemented for FOLIO"));
	}

	@Override
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs) {
		return Mono.error(new NotImplementedException("Update item status is not currently implemented for FOLIO"));
	}

	@Override
	public Mono<String> checkOutItemToPatron(String itemId, String patronBarcode) {
		// WARNING We might need to make this accept a patronIdentity - as different
		// systems might take different ways to identify the patron

		return Mono.error(new NotImplementedException("Check out item to patron is not currently implemented for FOLIO"));
	}

	@Override
	public Mono<String> deleteItem(String id) {
		return Mono.error(new NotImplementedException("Delete virtual item is not currently implemented for FOLIO"));
	}

	@Override
	public Mono<String> deleteBib(String id) {
		return Mono.error(new NotImplementedException("Delete virtual bib is not currently implemented for FOLIO"));
	}

	private <T, R> Mono<T> makeRequest(@NonNull MutableHttpRequest<R> request,
		@NonNull Argument<T> bodyType) {

		log.trace("Making request: {} to Host LMS: {}", request, getHostLmsCode());

		return Mono.from(httpClient.retrieve(request, bodyType))
			.doOnSuccess(response -> log.trace(
				"Received response: {} from Host LMS: {}", response, getHostLmsCode()))
			.doOnError(HttpClientResponseException.class,
				error -> log.trace("Received error response: {} from Host LMS: {}",
					toLogOutput(error.getResponse()), getHostLmsCode()));
	}

	private String toLogOutput(HttpResponse<?> response) {
		if (response == null) {
			return "No response included in error";
		}

		return "Status: \"%s\"\nHeaders: %s\nBody: %s\n".formatted(
			getValue(response, HttpResponse::getStatus),
			toLogOutput(response.getHeaders()),
			response.getBody(Argument.of(String.class))
		);
	}

	private String toLogOutput(HttpHeaders headers) {
		return headers.asMap().entrySet().stream()
			.map(entry -> "%s: %s".formatted(entry.getKey(), entry.getValue()))
			.collect(Collectors.joining("; "));
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

  public Mono<Boolean> supplierPreflight(String borrowingAgencyCode, String supplyingAgencyCode, String canonicalItemType, String canonicalPatronType) {
    return Mono.just(Boolean.TRUE);
  }
}

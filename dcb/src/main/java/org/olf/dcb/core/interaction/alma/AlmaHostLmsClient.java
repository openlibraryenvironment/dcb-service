package org.olf.dcb.core.interaction.alma;

import static io.micrometer.common.util.StringUtils.isBlank;
import static org.olf.dcb.core.model.FunctionalSettingType.VIRTUAL_PATRON_NAMES_VISIBLE;
import static org.olf.dcb.core.model.WorkflowConstants.EXPEDITED_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.ReactorUtils.raiseError;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.interops.ConfigType;
import org.zalando.problem.Problem;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.AlmaCircDesk;
import services.k_int.interaction.alma.AlmaLibraryResponse;
import services.k_int.interaction.alma.AlmaLocation;
import services.k_int.interaction.alma.types.AlmaBib;
import services.k_int.interaction.alma.types.AlmaGroupedLocationResponse;
import services.k_int.interaction.alma.types.AlmaUser;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.UserIdentifier;
import services.k_int.interaction.alma.types.WithAttr;
import services.k_int.interaction.alma.types.holdings.AlmaHolding;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaItemData;
import services.k_int.interaction.alma.types.items.AlmaItemLoan;
import services.k_int.interaction.alma.types.userRequest.AlmaRequest;
import services.k_int.interaction.alma.types.userRequest.AlmaRequestResponse;
import services.k_int.interaction.alma.types.userRequest.AlmaRequests;
import services.k_int.utils.UUIDUtils;

@Slf4j
@Prototype
// @See https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/3234496514/ALMA+Integration
public class AlmaHostLmsClient implements HostLmsClient {
	private final HostLms hostLms;
	private final HttpClient httpClient;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final ConversionService conversionService;
	private final LocationService locationService;
	private final HostLmsService hostLmsService;
	private final AlmaApiClient client;
	private final AlmaClientConfig config;
	private final ConsortiumService consortiumService;

	public AlmaHostLmsClient(@Parameter HostLms hostLms,
		@Parameter("client") HttpClient httpClient,
		AlmaClientFactory almaClientFactory,
		ReferenceValueMappingService referenceValueMappingService,
		MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService,
		LocationToAgencyMappingService locationToAgencyMappingService,
		ConversionService conversionService,
		LocationService locationService,
		HostLmsService hostLmsService,
	 	ConsortiumService consortiumService) {

		this.hostLms = hostLms;
		this.httpClient = httpClient;
		this.materialTypeToItemTypeMappingService = materialTypeToItemTypeMappingService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.config = new AlmaClientConfig(hostLms);
		this.client = almaClientFactory.createClientFor(hostLms);
		this.referenceValueMappingService = referenceValueMappingService;
		this.conversionService = conversionService;
		this.locationService = locationService;
		this.hostLmsService = hostLmsService;
		this.consortiumService = consortiumService;
	}

	@Override
	public HostLms getHostLms() {
		return hostLms;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return config.getSettings();
	}
	
	// At most 4 items mapped at once per bib, each making one Alma call: Alma allows an institution 50 calls a second across every integration
	private static final int ALMA_REQUEST_CONCURRENCY = 4;

	@Override
	public Mono<List<Item>> getItems(BibRecord bib) {
		return client.retrieveAllItems(bib.getSourceRecordId())
			.flatMapSequential(almaItem -> Mono.defer(() -> mapAlmaItemToDCBItem(almaItem))
					.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, getHostLmsCode()))
					.flatMap(materialTypeToItemTypeMappingService::enrichItemWithMappedItemType)
					.onErrorResume(error -> Mono.just(unmappableItem(almaItem, error))),
				ALMA_REQUEST_CONCURRENCY)
			.collectList();
	}

	// Returned with the reason rather than dropped, so one bad record neither hides its bib's other items nor vanishes
	private Item unmappableItem(AlmaItem almaItem, Throwable error) {
		final var itemId = getValueOrNull(almaItem, AlmaItem::getItemData, AlmaItemData::getPid);

		log.warn("Could not map Alma item {} on {}", itemId, getHostLmsCode(), error);

		return Item.builder()
			.localId(itemId)
			.status(new ItemStatus(ItemStatusCode.UNKNOWN))
			.isRequestable(false)
			.sourceHostLmsCode(getHostLmsCode())
			.owningContext(getHostLmsCode())
			.decisionLogEntry("Could not map this Alma item: " + error.getMessage())
			.build();
	}

	// The minimum DCB should give Alma
	private record DCBHold(String localPatronId, String localItemId, Location pickupLocation, String note,
		String supplyingLocalItemLocation, String activeWorkflow) {}

	// This is the minimum we should know to place a hold in Alma (V1)
	private record MinimumAlmaHold(String localPatronId, String localItemId, String pickupLibraryCode,
		String comment, String dcbRequestId) {}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(hold -> EXPEDITED_WORKFLOW.equals(hold.activeWorkflow())
				? resolveLibraryFromLocationRecord(hold) : getDcbSharingLibraryCode())
			.flatMap(lib -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), lib, p.getNote(), p.getPatronRequestId())))
			.doOnSubscribe(s -> log.info("placeHoldRequestAtSupplyingAgency patron={} item={}",
				p.getLocalPatronId(), p.getLocalItemId()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(hold -> PICKUP_ANYWHERE_WORKFLOW.equals(hold.activeWorkflow())
				? getDcbSharingLibraryCode() : resolveLibraryFromLocationRecord(hold))
			.flatMap(lib -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), lib, p.getNote(), p.getPatronRequestId())))
			.doOnSubscribe(s -> log.info("placeHoldRequestAtBorrowingAgency patron={} item={}",
				p.getLocalPatronId(), p.getLocalItemId()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(this::resolveLibraryFromLocationRecord)
			.flatMap(lib -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), lib, p.getNote(), p.getPatronRequestId())))
			.doOnSubscribe(s -> log.info("placeHoldRequestAtPickupAgency patron={} item={}",
				p.getLocalPatronId(), p.getLocalItemId()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(this::resolveLibraryFromLocationRecord)
			.flatMap(lib -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), lib, p.getNote(), p.getPatronRequestId())))
			.doOnSubscribe(s -> log.info("placeHoldRequestAtLocalAgency patron={} item={}",
				p.getLocalPatronId(), p.getLocalItemId()));
	}

// === Library-code resolvers ===

	// Supplier: always use the configured "DCB library"
	// This is used to define a library outside the system
	// Note:we may want to use the location service to drive this
	// however we are following a pattern used in other hostlms
	private String getDcbSharingLibraryCode() {
		final var lib = config.getDcbSharingLibraryCode();
		if (isBlank(lib)) throw new IllegalStateException("Missing DCB sharing library code in config");
		return lib;
	}

	// Try to derive the Alma library code from pickupLocation.localId
	// expectation here is that all location records will have the localId value set
	private String resolveLibraryFromLocationRecord(DCBHold h) {
		final var loc = h.pickupLocation();
		if (loc == null) throw new IllegalArgumentException("PickupLocation is required");

		// IMPORTANT: localId is your Alma library code.
		final String candidate = getValueOrNull(loc, Location::getLocalId);
		log.debug("resolveLibraryFromPickup: localId={}", candidate);
		if (!isBlank(candidate)) return candidate;

		throw new IllegalArgumentException("PickupLocation.LocalId is required");
	}

// === Submission ===

	// At most 500 of the patron's active holds are searched for one already on the item
	private static final int MAX_HOLD_PAGES = 5;

	// A placement retried after Alma created the hold but DCB lost the response would otherwise place a second one
	private Mono<LocalRequest> submitLibraryHold(MinimumAlmaHold hold) {
		return findOwnActiveHold(hold)
			.doOnNext(existing -> log.info("Adopting existing Alma request {} patron={} item={}",
				existing.getRequestId(), hold.localPatronId(), hold.localItemId()))
			.switchIfEmpty(Mono.defer(() -> createLibraryHold(hold)))
			.map(this::mapAlmaRequestToLocalRequest);
	}

	/**
	 * Only a request carrying this patron request's own marker may be adopted.
	 * <p>
	 * At the borrowing agency the local patron id is the patron's real Alma account, so a
	 * hold they placed themselves sits in the same list; adopting it would hand DCB a
	 * request it did not place and cancel it on finalisation. Alma's user-request list is
	 * not documented to carry item_id, so the marker is also the only field we can rely on.
	 */
	private Mono<AlmaRequestResponse> findOwnActiveHold(MinimumAlmaHold hold) {
		final var marker = dcbMarker(hold.dcbRequestId());

		// With no marker to match on, a duplicate hold is a lesser fault than adopting a stranger's
		if (marker == null) return Mono.empty();

		return Flux.range(0, MAX_HOLD_PAGES)
			.concatMap(page -> client.retrieveUserHoldRequestsPage(hold.localPatronId(), page * AlmaApiClient.REQUEST_PAGE_SIZE))
			.takeUntil(page -> page.getRequests() == null || page.getRequests().size() < AlmaApiClient.REQUEST_PAGE_SIZE)
			.concatMapIterable(page -> page.getRequests() != null ? page.getRequests() : List.<AlmaRequestResponse>of())
			.filter(request -> request.getComment() != null && request.getComment().contains(marker))
			.next();
	}

	// Bracketed so one request id cannot match another that it is a prefix of
	private static String dcbMarker(String dcbRequestId) {
		return isBlank(dcbRequestId) ? null : "[DCB-REQUEST:" + dcbRequestId + "]";
	}

	private static String holdComment(MinimumAlmaHold hold) {
		final var marker = dcbMarker(hold.dcbRequestId());
		if (marker == null) return hold.comment();
		return isBlank(hold.comment()) ? marker : hold.comment() + " " + marker;
	}

	private Mono<AlmaRequestResponse> createLibraryHold(MinimumAlmaHold hold) {
		final var payload = AlmaRequest.builder()
			.requestType("HOLD")
			.pickupLocationType("LIBRARY")
			.pickupLocationLibrary(hold.pickupLibraryCode())
			.comment(holdComment(hold))
			.build();

		return client.createUserRequest(hold.localPatronId(), hold.localItemId(), payload)
			.doOnSubscribe(s -> log.info("Submitting HOLD patron={} item={} pickupLibrary={}",
				hold.localPatronId(), hold.localItemId(), hold.pickupLibraryCode()))
			.doOnError(this::logAlmaProblemDetails)
			.switchIfEmpty(raiseError(new AlmaHostLmsClientException(
				"Empty Alma response creating hold for patron "+hold.localPatronId()+" / item "+hold.localItemId())));
	}

	private void logAlmaProblemDetails(Throwable e) {
		if (e instanceof AlmaApiException almaError) {
			log.error("Hold request failed with Alma error codes {}", almaError.getErrorCodes());
		} else {
			log.error("Hold request failed: {}", e.toString());
		}
	}

	private Mono<DCBHold> validate(PlaceHoldRequestParameters p) {
		if (p == null) return Mono.error(new IllegalArgumentException("PlaceHoldRequestParameters is required"));
		if (isBlank(p.getLocalPatronId())) return Mono.error(new IllegalArgumentException("localPatronId is required"));
		if (isBlank(p.getLocalItemId())) return Mono.error(new IllegalArgumentException("localItemId (item_pid) is required"));
		if (p.getPickupLocation() == null) return Mono.error(new IllegalArgumentException("pickupLocation is required"));
		if (p.getActiveWorkflow() == null) return Mono.error(new IllegalArgumentException("activeWorkflow is required"));
		return Mono.just(new DCBHold(p.getLocalPatronId(), p.getLocalItemId(), p.getPickupLocation(), p.getNote(),
			p.getSupplyingLocalItemLocation(), p.getActiveWorkflow()));
	}

	@Override
	public Mono<Map<String, Object>> fetchConfigurationFromAPI(ConfigType type) {
		return fetchLocations().map(response -> {
			Map<String, Object> config = new HashMap<>();
			config.put("locations", response.getLocations());
			return config;
		});
	}

	public Mono<AlmaGroupedLocationResponse> fetchLocations() {
		return client.retrieveLibraries()
			.flatMapMany(librariesResponse -> {
				List<AlmaLibraryResponse> libraries = librariesResponse.getLibraries();
				if (libraries == null || libraries.isEmpty()) {
					return Flux.empty();
				}
				return Flux.fromIterable(libraries)
					.flatMap(library -> {
						String libraryCode = getValueOrNull(library, AlmaLibraryResponse::getCode);
						String libraryName = getValueOrNull(library, AlmaLibraryResponse::getName);
						if (library.getNumberOfLocations().getValue() > 0 && libraryCode != null) {
							return client.retrieveLocations(libraryCode)
								.flatMapMany(response -> {
									List<AlmaLocation> locations = response.getLocations();
									log.debug("locations for library {}: {}", libraryCode, locations);
									if (locations == null || locations.isEmpty()) {
										return Flux.empty();
									}
									return Flux.fromIterable(locations)
										.doOnNext(location -> location.setLibraryCode(libraryCode))
										.doOnNext(location -> location.setLibraryName(libraryName));
								})
								.onErrorResume(e -> {
									log.warn("Failed to fetch locations for library ID {}: {}", libraryCode, e.getMessage());
									return Flux.empty();
								});
						}
						return Flux.empty();
					});
			})
			.onErrorContinue((throwable, location) -> {
				log.warn("Error for location {}: {}", location, throwable.getMessage() != null ? throwable.getMessage() : throwable.toString());
			})
			.collectList()
			.map(locations -> AlmaGroupedLocationResponse.builder().locations(locations).build());
	}

	/** ToDo: This should be a default method I think */
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

	/** ToDo: This should be a default method I think */
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
		return client.getUserDetails(localPatronId)
			.map(this::almaUserToPatron);
	}

	@Override
	public Mono<Patron> getPatronByIdentifier(String id) {
		return client.getUserDetails(id)
			.map(this::almaUserToPatron)
			.onErrorResume(AlmaHostLmsClient::isVirtualPatronNotFoundError, error -> Mono.empty());
	}

	@Override
	public Mono<Patron> getPatronByUsername(String localUsername) {
		return client.getUserDetails(localUsername)
			.map(this::almaUserToPatron);
	}

	@Override
	public Mono<Integer> countHoldsForPatron(String localPatronId) {
		log.debug("countHoldsForPatron({})", localPatronId);

		// total_record_count spans every page, so Alma's page size cannot cap the count
		return client.retrieveUserHoldRequests(localPatronId)
			.mapNotNull(AlmaRequests::getRecordCount)
			// An error here must not be reported as a count of zero
			.doOnError(error -> log.warn("Could not count holds for patron {} at {}",
				localPatronId, getHostLmsCode(), error))
			.onErrorResume(error -> Mono.empty());
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		final var uniqueId = getValueOrNull(patron, org.olf.dcb.core.model.Patron::determineUniqueId);

		if (uniqueId == null) {
			return Mono.error(new IllegalArgumentException("Unable to find uniqueId for virtual patron"));
		}

		// this relies on implementation relies on alma finding the user by our uniqueId
		// if we have created a user with a user identifier correctly we should be good
		// the user will need to have the user identifier enabled
		return client.getUserDetails(uniqueId)
			.doOnNext(almaUser -> log.info("Found virtual patron with uniqueId: {}", uniqueId))
			.flatMap(this::checkAndUpdateExpiryIfNeeded)
			.map(this::almaUserToPatron)
			.onErrorResume(e -> {
				if (isVirtualPatronNotFoundError(e)) {
					throw createVirtualPatronNotFoundException(uniqueId, e);
				}
				return Mono.error(e);
			});
	}

	private static boolean isVirtualPatronNotFoundError(Throwable e) {
		return e instanceof AlmaApiException almaError && almaError.has(AlmaApiException.Code.USER_NOT_FOUND);
	}

	private VirtualPatronNotFound createVirtualPatronNotFoundException(String uniqueId, Throwable cause) {
		return VirtualPatronNotFound.builder()
			.withDetail("No records found")
			.with("uniqueId", uniqueId)
			.with("Response", cause.toString())
			.build();
	}

	// Static create patron defaults
	private static final String DEFAULT_FIRST_NAME = "DCB";
	private static final String DEFAULT_LAST_NAME = "VPATRON";
	private static final String RECORD_TYPE_PUBLIC = "PUBLIC";
	private static final String STATUS_ACTIVE = "ACTIVE";
	private static final String ACCOUNT_TYPE_EXTERNAL = "EXTERNAL";
	private static final String ID_TYPE_BARCODE = "BARCODE";
	private static final String ID_TYPE_INST_ID = "INST_ID";

	@Override
	public Mono<String> createPatron(Patron patron) {
		return consortiumService.isEnabled(VIRTUAL_PATRON_NAMES_VISIBLE)
			.flatMap(namesVisible -> {
				// Only use real names if the setting is explicitly enabled
				final var firstName = namesVisible ? extractFirstName(patron) : DEFAULT_FIRST_NAME;
				final var lastName = namesVisible ? extractLastName(patron) : DEFAULT_LAST_NAME;

				final var externalId = extractExternalId(patron);

				List<UserIdentifier> userIdentifiers = createUserIdentifiers(patron);
				AlmaUser almaUser = buildAlmaUser(firstName, lastName, externalId, userIdentifiers);

				return determinePatronType(patron)
					.flatMap(patronType -> {
						almaUser.setUser_group(CodeValuePair.builder().value(patronType).build());

						return Mono.from(client.createUser(almaUser))
							.map(AlmaUser::getPrimary_id);
					});
			});
	}

	private String extractFirstName(Patron patron) {
		if (hasLocalNames(patron)) {
			return patron.getLocalNames().get(0);
		}
		return DEFAULT_FIRST_NAME;
	}

	private String extractLastName(Patron patron) {
		if (hasLocalNames(patron)) {
			return patron.getLocalNames().get(patron.getLocalNames().size() - 1);
		}
		return DEFAULT_LAST_NAME;
	}

	private boolean hasLocalNames(Patron patron) {
		return patron.getLocalNames() != null && !patron.getLocalNames().isEmpty();
	}

	private String extractExternalId(Patron patron) {
		if (patron.getUniqueIds() != null && !patron.getUniqueIds().isEmpty()) {
			return patron.getUniqueIds().get(0);
		}
		return null;
	}

	private List<UserIdentifier> createUserIdentifiers(Patron patron) {
		final var identifierType = config.getUserIdentifier(ID_TYPE_INST_ID);
		final String externalId = extractExternalId(patron);

		// Validate that we have at least one identifier source
		if ((patron.getLocalBarcodes() == null || patron.getLocalBarcodes().isEmpty()) && externalId == null) {
			throw new IllegalArgumentException("Cannot create user identifiers: patron has no barcodes or external ID");
		}

		List<UserIdentifier> identifiers = new ArrayList<>();

		// Add barcode identifiers
		// WARNING: adding multiple barcodes may not be supported by Alma
		if (patron.getLocalBarcodes() != null && !patron.getLocalBarcodes().isEmpty()) {
			patron.getLocalBarcodes().stream()
				.filter(Objects::nonNull) // Guard against null barcodes in the list
				.filter(barcode -> !barcode.equals(externalId)) // Request cannot contain two identifiers with the same value
				.map(barcode -> UserIdentifier.builder()
					.id_type(WithAttr.builder().value(ID_TYPE_BARCODE).build())
					.value(barcode)
					.build())
				.forEach(identifiers::add);
		}

		// Add external ID identifier (if available and not already added as barcode)
		if (externalId != null) {
			identifiers.add(UserIdentifier.builder()
				.id_type(WithAttr.builder().value(identifierType).build())
				.value(externalId)
				.build());
		}
		return identifiers;
	}

	private AlmaUser buildAlmaUser(String firstName, String lastName, String externalId,
																 List<UserIdentifier> userIdentifiers) {
		return AlmaUser.builder()
			.record_type(CodeValuePair.builder().value(RECORD_TYPE_PUBLIC).build())
			.first_name(firstName)
			.last_name(lastName)
			.status(CodeValuePair.builder().value(STATUS_ACTIVE).build())
			.is_researcher(Boolean.FALSE)
			.identifiers(userIdentifiers)
			.external_id(externalId)
			.account_type(CodeValuePair.builder().value(ACCOUNT_TYPE_EXTERNAL).build())
			.build();
	}

	private Mono<String> determinePatronType(Patron patron) {
		return (patron.getLocalPatronType() != null)
			? Mono.just(patron.getLocalPatronType())
			: findLocalPatronType(patron.getCanonicalPatronType());
	}

	@Override
	public Mono<String> createBib(Bib bib) {
		final var author = (bib.getAuthor() != null) ? bib.getAuthor() : null;
		final var title = (bib.getTitle() != null) ? bib.getTitle() : null;

		final var alma_bib = AlmaXmlGenerator.createBibXml(title, author);

		return client.createBibRecord(alma_bib)
			.map(AlmaBib::getMmsId);
	}

	@Override
	public Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters) {
		final var userId = getValueOrNull(parameters, CancelHoldRequestParameters::getPatronId);
		final var localRequestId = getValueOrNull(parameters, CancelHoldRequestParameters::getLocalRequestId);

		return client.cancelUserRequest(userId, localRequestId, config.getRequestCancellationReason())
			.thenReturn(parameters.getLocalRequestId());
	}

	// At most 500 of the patron's loans are searched for the one being renewed
	private static final int MAX_LOAN_PAGES = 5;

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal renewal) {
		log.info("Starting direct renewal for patron {} and item {}", renewal.getLocalPatronId(), renewal.getLocalItemId());
		final String patronId = renewal.getLocalPatronId();
		final String itemId = renewal.getLocalItemId();

		if (itemId == null || itemId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Local Item ID is missing and required for renewal."));
		}

		return Flux.range(0, MAX_LOAN_PAGES)
			.concatMap(page -> client.retrieveUserLoansPage(patronId, page * AlmaApiClient.LOAN_PAGE_SIZE))
			.takeUntil(page -> page.getLoans() == null || page.getLoans().size() < AlmaApiClient.LOAN_PAGE_SIZE)
			.concatMapIterable(page -> page.getLoans() != null ? page.getLoans() : List.<AlmaItemLoan>of())
			.filter(loan -> itemId.equals(loan.getItemId()))
			.next()
			.switchIfEmpty(Mono.error(new IllegalStateException("Could not find a matching loan for item ID " + itemId + " and patron " + patronId)))
			.flatMap(matchedLoan -> {
				final String loanId = matchedLoan.getLoanId();
				log.info("Found matching loan ID: {}. Proceeding with renewal.", loanId);
				return client.renewLoan(patronId, loanId);
			})
			.map(renewedLoan -> {
				log.info("Renewal successful for loan {}. New due date: {}", renewedLoan.getLoanId(), renewedLoan.getDueDate());
				return renewal;
			})
			.doOnError(error -> log.error("Direct renewal process failed for item {}: {}", itemId, error.getMessage()));
	}

	@Override
	public Mono<LocalRequest> updateHoldRequest(LocalRequest req) {
		return Mono.defer(() -> {
			final String bibId = require(req.getBibId(), "Bib ID");
			final String holdingId = require(req.getHoldingId(), "Holding ID");
			final String itemId = require(req.getRequestedItemId(), "Item ID");
			final String barcode = require(req.getRequestedItemBarcode(), "Item Barcode");
			final String canonicalItemType = require(req.getCanonicalItemType(), "Canonical Item Type");

			Mono<String> mappedType = getMappedItemType(canonicalItemType)
				.switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown canonical item type: " + canonicalItemType)));

			return Mono.zip(client.retrieveItem(bibId, holdingId, itemId), mappedType)
				.map(t -> applyUpdates(t.getT1(), barcode, t.getT2()))
				.flatMap(updated -> client.updateItem(bibId, holdingId, itemId, updated))
				.thenReturn(req);
		});
	}

	private static <T> T require(T value, String name) {
		if (value == null) throw new IllegalArgumentException(name + " is required for updating a hold request.");
		return value;
	}

	private static AlmaItem applyUpdates(AlmaItem item, String newBarcode, String newType) {
		var data = item.getItemData();

		// here we do a dance to avoid an INTERNAL_SERVER_ERROR
		// the update item endpoint is sensitive to the fields that are sent
		// before removing or adding be sure to test manually
		// Doc: https://developers.exlibrisgroup.com/alma/apis/dgit ocs/xsd/rest_item.xsd/?tags=PUT
		var minimalRequestBody = AlmaItemData.builder()
			// update fields
			.barcode(newBarcode)
			.physicalMaterialType(CodeValuePair.builder().value(newType).build())
			// unchanged required fields
			.pid(data.getPid())
			.policy(data.getPolicy())
			.library(data.getLibrary())
			.location(data.getLocation())
			.build();

		return AlmaItem.builder().itemData(minimalRequestBody).build();
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		// Alma replaces every field and list on PUT, so the fetched user goes back with only the group changed
		return client.getUserDetails(localId)
			.map(user -> {
				user.setUser_group(CodeValuePair.builder().value(patronType).build());
				return user;
			})
			// Alma keeps an external user's existing user_group unless the PUT names it in override
			.flatMap(user -> client.updateUserDetails(localId, user, Map.of("override", "user_group")))
			.map(this::almaUserToPatron);
	}

	// Alma can only verify a password held by the Ex Libris Identity Service; any other profile would be a pretence
	private static final String PASSWORD_AUTH_PROFILE = "BASIC/BARCODE+PASSWORD";

	@Override
	public Mono<Patron> patronAuth(String authProfile, String barcode, String secret) {
		if (!PASSWORD_AUTH_PROFILE.equals(authProfile)) {
			return Mono.error(new IllegalStateException("Alma supports auth profile "
				+ PASSWORD_AUTH_PROFILE + ", not \"" + authProfile + "\", on " + getHostLmsCode()));
		}

		if (isBlank(barcode) || isBlank(secret)) {
			return Mono.empty();
		}

		return client.authenticateUser(barcode, secret)
			.then(Mono.defer(() -> client.getUserDetails(barcode)))
			.map(this::almaUserToPatron)
			.onErrorResume(HttpClientResponseException.class, error -> isCredentialRejection(error)
				? Mono.empty()
				: Mono.error(error));
	}

	private static boolean isCredentialRejection(HttpClientResponseException error) {
		final int code = error.getStatus().getCode();
		return code >= 400 && code < 500 && code != 429;
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

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		String bibId = getValueOrNull(cic, CreateItemCommand::getBibId);
		String policy = config.getItemPolicy("BOOK");
		String baseStatus = "1";
		String callNumber = "DCB_VIRTUAL_COLLECTION";
		String holdingNote = "DCB Virtual holding record";

		String targetLibraryCode = config.getVirtualItemLibraryCode();

		log.info("Create item for Alma with {}. Targeting Library: {}", cic, targetLibraryCode);

		return Mono.zip(
				requireVirtualLocation(targetLibraryCode),
				getMappedItemType(cic.getCanonicalItemType())
			)
			.flatMap(tuple -> {
				AlmaLocation location = tuple.getT1();
				String itemType = tuple.getT2();

				String holdingXml = buildHoldingXml(location, callNumber, holdingNote);
				AlmaItem item = buildAlmaItem(cic, location, policy, baseStatus, itemType);

				return createHolding(bibId, holdingXml)
					.flatMap(holding -> client.createItem(bibId, holding.getHoldingId(), item)
						.map(created -> mapToHostLmsItem(created.getItemData(), holding.getHoldingId(), bibId))
						.onErrorResume(error -> deleteOrphanedHolding(bibId, holding.getHoldingId())
							.then(Mono.<HostLmsItem>error(error))));
			});
	}

	// DCB records no holding id until the item exists, so a holding left here could never be cleaned up later
	private Mono<Void> deleteOrphanedHolding(String bibId, String holdingId) {
		return client.deleteHoldingsRecord(bibId, holdingId)
			.doOnError(error -> log.error("Could not delete Alma holding {} on bib {} at {} after item creation failed",
				holdingId, bibId, getHostLmsCode(), error))
			.onErrorResume(error -> Mono.empty())
			.then();
	}

	private String buildHoldingXml(AlmaLocation location, String callNumber, String note) {
		return AlmaXmlGenerator.generateHoldingXml(
			location.getLibraryCode(),
			location.getCode(),
			callNumber,
			note
		);
	}

	private AlmaItem buildAlmaItem(CreateItemCommand cic, AlmaLocation location, String policy, String status, String itemType) {
		return AlmaItem.builder()
			.itemData(
				AlmaItemData.builder()
					.barcode(cic.getBarcode())
					.physicalMaterialType(CodeValuePair.builder().value(itemType).build())
					.policy(CodeValuePair.builder().value(policy).build())
					.baseStatus(CodeValuePair.builder().value(status).build())
					.description("DCB copy")
					.statisticsNote1("DCB item")
					.publicNote("Virtual item = created by DCB")
					.fulfillmentNote("Virtual item = created by DCB")
					.internalNote1("Virtual item = created by DCB")
					.holdingData(
						AlmaHolding.builder()
							.library(CodeValuePair.builder().value(location.getLibraryCode()).build())
							.location(CodeValuePair.builder().value(location.getCode()).build())
							.build()
					)
					.build()
			)
			.build();
	}

	private HostLmsItem mapToHostLmsItem(AlmaItemData itemData, String holdingId, String bibId) {
		return HostLmsItem.builder()
			.localId(itemData.getPid())
			.barcode(itemData.getBarcode())
			.status(deriveItemStatus(itemData).getCode().name())
			.holdingId(holdingId)
			.bibId(bibId)
			.build();
	}

	private Mono<AlmaHolding> createHolding(String bibId, String almaHolding) {
		return client.createHoldingRecord(bibId, almaHolding);
	}

	@Override
	public Mono<HostLmsRequest> getRequest(HostLmsRequest request) {
		final var localRequestId = getValueOrNull(request, HostLmsRequest::getLocalId);
		final var patronId = getValueOrNull(request, HostLmsRequest::getLocalPatronId);

		return client.retrieveUserRequest(patronId, localRequestId)
			.map(almaRequest -> {

				final var itemId = getValueOrNull(almaRequest, AlmaRequestResponse::getItemId);
				final var itemBarcode = getValueOrNull(almaRequest, AlmaRequestResponse::getItemBarcode);
				final var rawStatus = getValueOrNull(almaRequest, AlmaRequestResponse::getRequestStatus);

				return HostLmsRequest.builder()
					.localId(almaRequest.getRequestId())
					.status(checkHoldStatus(rawStatus))
					.rawStatus(rawStatus)
					.requestedItemId(itemId)
					.requestedItemBarcode(itemBarcode)
					.build();
			});
	}

	// A user request's request_status is only ever NOT_STARTED, IN_PROCESS or ON_HOLD_SHELF (rest_user_request.xsd)
	private String checkHoldStatus(String status) {
		if (status == null) {
			return null;
		}

		return switch (status) {
			case "NOT_STARTED", "IN_PROCESS" -> HostLmsRequest.HOLD_CONFIRMED;
			case "ON_HOLD_SHELF" -> HostLmsRequest.HOLD_READY;
			default -> status;
		};
	}

	@Override
	public Mono<HostLmsItem> getItem(HostLmsItem hostLmsItem) {
		// As in getItems, we need another call to get the hold count for this item

		final String bibId = hostLmsItem.getBibId();
		final String holdingId = hostLmsItem.getHoldingId();
		final String itemId = hostLmsItem.getLocalId();
		// First get the item

		return client.retrieveItem(bibId, holdingId, itemId)
			.doOnError(e -> log.error("Failed to retrieve Alma item {}. BibId: {}, HoldingId: {}",
				itemId, bibId, holdingId, e))
			.flatMap(item -> {

				final var almaItemData = getValueOrNull(item, AlmaItem::getItemData);

				Mono<Integer> holdCountMono = client.retrieveItemRequests(bibId, holdingId, itemId)
					.map(requests -> (requests.getRecordCount() != null) ? requests.getRecordCount() : 0)
					.doOnError(e -> log.warn("Failed to retrieve hold count for Alma item {}. Defaulting to 0.", itemId, e))
					.onErrorReturn(0);
				// Now bring it all together. Same 0 fallback for hold counts we can't get
				return holdCountMono.map(holdCount -> {
					var returnHostLmsItem = HostLmsItem.builder()
						.localId(almaItemData.getPid())
						.barcode(almaItemData.getBarcode())
						.bibId(bibId)
						.holdingId(holdingId)
						.holdCount(holdCount)
						.build();

					returnHostLmsItem = deriveItemStatusFromProcessType(returnHostLmsItem, almaItemData);

					return returnHostLmsItem;
				});
			});
	}

	@Override
	public Mono<String> updateItemStatus(HostLmsItem hostLmsItem, CanonicalItemState crs) {
		return switch (crs) {
			case TRANSIT, RECEIVED, COMPLETED -> scanInAtOwningLibrary(hostLmsItem);
			case AVAILABLE, OFFSITE, MISSING, ONHOLDSHELF -> Mono.error(new UnsupportedOperationException(
				"Alma has no item action for state " + crs));
		};
	}

	private Mono<String> scanInAtOwningLibrary(HostLmsItem hostLmsItem) {
		final var bibId = getValueOrNull(hostLmsItem, HostLmsItem::getBibId);
		final var holdingsId = getValueOrNull(hostLmsItem, HostLmsItem::getHoldingId);
		final var itemId = getValueOrNull(hostLmsItem, HostLmsItem::getLocalId);
		log.debug("Updating item {} with bibId {} and holdingsId {}", itemId, bibId, holdingsId);

		return client.retrieveItem(bibId, holdingsId, itemId)
			.map(data -> {
				final var almaItemData = getValueOrNull(data, AlmaItem::getItemData);
				// working theory as of 17/09/2025
				// that the item possesses the location we want to scan in at
				final var libraryCode = getValueOrNull(almaItemData, AlmaItemData::getLibrary, CodeValuePair::getValue);
				// each location should have its default circ desk set
				// the intention to override this is to handle the default code changing for a system
				final var defaultCircDesk = config.getDefaultCircDeskCode("DEFAULT_CIRC_DESK");

				return new ScanInQuery(bibId, holdingsId, itemId, libraryCode, defaultCircDesk);
			})
			.flatMap(client::scanIn)
			.map(data -> {
				final var almaItemData = getValueOrNull(data, AlmaItem::getItemData);
				final var baseStatus = getValueOrNull(almaItemData, AlmaItemData::getBaseStatus, CodeValuePair::getValue);
				final var processType = getValueOrNull(almaItemData, AlmaItemData::getProcess_type, CodeValuePair::getValue);
				log.debug("Updated item {} with baseStatus {} and processType {}", itemId, baseStatus, processType);

				return "OK";
			});
	}

	public record ScanInQuery(String mms_id, String holding_id, String item_pid, String library, String circ_desk) {}

	@Override
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkoutItemCommand) {
		// Get the ID of the patron, the local request and the pickup circ desk
		final var patronId = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getPatronId);
		final var requestId = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getLocalRequestId);
		final var pickupLocationCircuationDesk = config.getPickupCircDesk("DEFAULT_CIRC_DESK");
		// The item's local Alma ID (PID) and its barcode should both be obtainable from the checkout command
		final var itemId = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getItemId);
		final var itemBarcode = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getItemBarcode);

		// With the method we are using, we need the barcode.
		if (isBlank(itemBarcode)) {
			log.error("Cannot perform checkout for item {}: item barcode is missing from CheckoutItemCommand.", itemId);
			return Mono.error(new IllegalArgumentException("Item barcode is required for Alma checkout to determine library code."));
		}

		log.info("Alma: Checking out item {} (barcode: {}) to patron {} (request: {}). Fetching item details to confirm library...",
			itemId, itemBarcode, patronId, requestId);

		// Use the barcode to fetch the full item data and get the correct library code from that
		// If this ever fails, we will need to switch to finding the holding / bib and go from there
		return client.retrieveItemBarcodeOnly(itemBarcode)
			.flatMap(almaItem -> {

				final var correctLibraryCode = Optional.ofNullable(almaItem.getItemData())
					.map(AlmaItemData::getLibrary)
					.map(CodeValuePair::getValue)
					.filter(s -> !s.isBlank())
					.orElse(null);

				if (correctLibraryCode == null) {
					log.error("Failed to extract library code from Alma item data for barcode {}", itemBarcode);
					return Mono.error(new IllegalStateException("Could not determine library code for item barcode: " + itemBarcode));
				}

				log.info("Alma: Proceeding with checkout for item {} at library {}, circ desk {}",
					itemId, correctLibraryCode, pickupLocationCircuationDesk);

				// Then just build the loan as we did before but with a new code
				AlmaItemLoan almaItemLoan = AlmaItemLoan.builder()
					.circDesk(CodeValuePair.builder().value(pickupLocationCircuationDesk).build())
					.returnCircDesk(CodeValuePair.builder().value(pickupLocationCircuationDesk).build())
					.library(CodeValuePair.builder().value(correctLibraryCode).build())
					.requestId(CodeValuePair.builder().value(requestId).build())
					.build();

				return client.createUserLoan(patronId, itemId, almaItemLoan);
			})
			.thenReturn("OK")
			.doOnError(e -> log.error("Alma checkout API call failed for patron {} and item {}: {}", patronId, itemId, e.getMessage()));
	}

	@Override
	public Mono<String> deleteItem(DeleteCommand deleteCommand) {
		final var id = getValueOrNull(deleteCommand, DeleteCommand::getItemId);
		final var holdingsId = getValueOrNull(deleteCommand, DeleteCommand::getHoldingsId);
		final var mms_id = getValueOrNull(deleteCommand, DeleteCommand::getBibId);

		return client.withdrawItem(mms_id, holdingsId, id)
			.flatMap(result -> client.deleteHoldingsRecord(mms_id, holdingsId));
	}

	@Override
	public Mono<String> deleteHold(DeleteCommand deleteCommand) {
		final var userId = getValueOrNull(deleteCommand, DeleteCommand::getPatronId);
		final var requestId = getValueOrNull(deleteCommand, DeleteCommand::getRequestId);

		log.debug("deleteHold({},{})", userId, requestId);

		return client.cancelUserRequest(userId, requestId, config.getRequestCancellationReason());
	}

  public Mono<String> deletePatron(String id) {
		return Mono.from(client.deleteUser(id))
			.then(Mono.just("OK"));
	}

	@Override
	public Mono<String> deleteBib(String id) {
		return Mono.from(client.deleteBibRecord(id))
			.then(Mono.just("OK"));
	}

	@Override
	public @NonNull String getClientId() {
		// Resolving "/" forces URI.toString to construct a fresh representation
		// rather than echoing whatever string the config supplied, so two tenants
		// configured with cosmetically different URLs still compare correctly.
		return qualifySystemIdentity(config.getBaseUrl().resolve("/").toString());
	}

	@Override
	public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
		// Alma refuses to renew an item with an active request, and the supplier hold DCB places is that request.
		// The note this used to append to the library's own item was never removed once the request finished.
		return Mono.empty();
	}

  @Override
  public Mono<Boolean> supplierPreflight(String borrowingAgencyCode, String supplyingAgencyCode, String canonicalItemType, String canonicalPatronType) {
    log.debug("ALMA Supplier Preflight {} {} {} {}",borrowingAgencyCode,supplyingAgencyCode,canonicalItemType,canonicalPatronType);
    return Mono.just(Boolean.TRUE);
  }

	@Override
	public Mono<String> checkInItem(CheckInItemCommand checkInItemCommand){
		final String bibId = checkInItemCommand.getBibId();
		final String holdingsId = checkInItemCommand.getHoldingId();
		final String itemId = checkInItemCommand.getItemId();

		log.debug("Checking in item {} with bibId {} and holdingsId {}", itemId, bibId, holdingsId);

		final String defaultCircDesk = config.getDefaultCircDeskCode("DEFAULT_CIRC_DESK");

		return client.scanIn(new ScanInQuery(bibId, holdingsId, itemId, config.getVirtualItemLibraryCode(), defaultCircDesk))
			.thenReturn("OK")
			.doOnError(e -> log.warn("Failed to check in Alma item {}", itemId, e));
	}

	private Patron almaUserToPatron(AlmaUser almaUser) {
		List<String> localIds = new ArrayList<String>();
		List<String> uniqueIds = new ArrayList<String>();
		List<String> localBarcodes = new ArrayList<String>();
		List<String> localNames = new ArrayList<String>();

		if ( almaUser.getPrimary_id() != null ) {
			localIds.add(almaUser.getPrimary_id());
		}

		if ( almaUser.getExternal_id() != null ) {
			uniqueIds.add(almaUser.getExternal_id());
		}

		localNames.add(almaUser.getFirst_name());
		localNames.add(almaUser.getLast_name());

		final var barcodeIdentifiers = Optional.ofNullable(almaUser.getIdentifiers()).orElse(List.of()).stream()
			.filter(identifier -> ID_TYPE_BARCODE.equals(
				getValueOrNull(identifier, UserIdentifier::getId_type, WithAttr::getValue)))
			.map(UserIdentifier::getValue)
			.filter(Objects::nonNull)
			.toList();

		// An institution's primary id is often an SIS or IdP id, so it stands in only for a user with no barcode
		if (barcodeIdentifiers.isEmpty()) {
			localBarcodes.add(almaUser.getPrimary_id());
		} else {
			localBarcodes.addAll(barcodeIdentifiers);
		}

		// An absent status is treated as active — only an explicit INACTIVE/DELETED blocks the patron.
		final var status = almaUser.getStatus() != null ? almaUser.getStatus().getValue() : null;
		final var isDeleted = "DELETED".equalsIgnoreCase(status);
		final var isActive = !isDeleted && !"INACTIVE".equalsIgnoreCase(status);

		final var isBlocked = Optional.ofNullable(almaUser.getUser_blocks()).orElse(List.of()).stream()
			.anyMatch(block -> "ACTIVE".equalsIgnoreCase(block.getBlock_status()));

		final var expiryDate = parseAlmaExpiryDate(almaUser.getExpirationDate(), almaUser.getPrimary_id());

		return Patron.builder()
			.localId(localIds) // list
			.localNames(localNames)
			.localBarcodes(localBarcodes)
			.uniqueIds(uniqueIds)
			.localPatronType(getValueOrNull(almaUser, AlmaUser::getUser_group, CodeValuePair::getValue))
			.expiryDate(expiryDate != null ? Timestamp.valueOf(expiryDate.atStartOfDay()) : null)
			.isDeleted(isDeleted)
			.isBlocked(isBlocked)
			.isActive(isActive)
			.build();
	}

	private LocalDate parseAlmaExpiryDate(String rawExpiryDate, String primaryId) {
		if (rawExpiryDate == null || rawExpiryDate.isBlank()) {
			return null;
		}

		final var trimmed = rawExpiryDate.trim();
		// Strip a time portion if one is present, then the trailing zone designator.
		final var datePart = trimmed.contains("T")
			? trimmed.substring(0, trimmed.indexOf('T'))
			: trimmed.replace("Z", "");

		try {
			return LocalDate.parse(datePart);
		} catch (DateTimeParseException e) {
			log.error("Failed to parse Alma expiry date \"{}\" for patron {}. Patron will be mapped without an expiry date.",
				rawExpiryDate, primaryId, e);

			return null;
		}
	}

	// An unreadable date leaves the due date unknown rather than making the whole item unmappable
	private Instant parseDueDate(String rawDueDate, String itemId) {
		if (rawDueDate == null || rawDueDate.isBlank()) {
			return null;
		}

		try {
			return Instant.parse(rawDueDate.trim());
		} catch (DateTimeParseException e) {
			log.warn("Could not read Alma due date \"{}\" for item {}", rawDueDate, itemId);
			return null;
		}
	}

	public Mono<PingResponse> ping() {
		Instant start = Instant.now();
		return Mono.from(client.test())
			.flatMap( tokenInfo -> {
				return Mono.just(PingResponse.builder()
					.target(getHostLmsCode())
					.versionInfo(getHostSystemType()+":"+getHostSystemVersion())
					.status("OK")
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
		return "ALMA";
	}

	public String getHostSystemVersion() {
		return "v1";
	}

	/**
	 * Builds a DCB Item from an AlmaItem. Now includes async call for the hold count
	 *
	 * @param almaItem The AlmaItem retrieved from the API
	 * @return A Mono<Item> containing the fully populated DCB Item
	 */
	private Mono<Item> mapAlmaItemToDCBItem(AlmaItem almaItem) {
		final String bibId = almaItem.getBibData().getMmsId();
		final String holdingId = almaItem.getHoldingData().getHoldingId();
		final String itemId = almaItem.getItemData().getPid();

		// Note: hold counts can sometimes be obtained from internal note 3
		// But we cannot assume this
		// If we could, then we could remove the need for an extra call and make this a lot simpler.

		return client.retrieveItemRequests(bibId, holdingId, itemId)
			.map(requests -> Optional.ofNullable(requests.getRecordCount()))
			// An unknown hold count is not a count of zero
			.onErrorResume(e -> {
				log.warn("Failed to retrieve hold count for item {} (bib: {}, holding: {}): {}",
					itemId, bibId, holdingId, e.getMessage());
				return Mono.just(Optional.<Integer>empty());
			})
			.map(holdCount -> {
				// Now we have the hold count, we can build the item.
				ItemStatus derivedItemStatus = deriveItemStatus(almaItem.getItemData());
				Boolean isRequestable = (derivedItemStatus.getCode() == ItemStatusCode.AVAILABLE);

				final Instant dueDate = parseDueDate(almaItem.getItemData().getDueDate(), itemId);

				// Alma's "library" is the branch that owns the item. Alma's "location" is the
				// shelving location within it - REF, STACKS, JUV - and every library on a
				// tenant draws from the same vocabulary, so it can never identify which
				// library an item belongs to. This used to build the item's Location from
				// the shelving location, which meant location-to-agency mapping was being
				// asked to map "STACKS" to a library.
				final var itemData = almaItem.getItemData();

				final var owningLibrary = itemData.getLibrary() != null
					? itemData.getLibrary().getValue()
					: null;

				final var shelvingLocation = itemData.getLocation() != null
					? itemData.getLocation().getValue()
					: null;

				Location derivedLocation = owningLibrary != null
					? locationForLibraryCode(owningLibrary)
					: null;

				Boolean derivedSuppression = ((almaItem.getBibData().getSuppressFromPublishing() != null) &&
					(almaItem.getBibData().getSuppressFromPublishing().equalsIgnoreCase("true")));

				return Item.builder()
					.localId(itemId)
					.status(derivedItemStatus)
					.dueDate(dueDate)
					.location(derivedLocation)
					.shelvingLocation(shelvingLocation)
					.barcode(almaItem.getItemData().getBarcode())
					.callNumber(almaItem.getHoldingData().getCallNumber())
					.isRequestable(isRequestable)
					.holdCount(holdCount.orElse(null))
					.localBibId(bibId)
					// this item type looks to be used for auditing
					.localItemType(almaItem.getItemData().getPhysicalMaterialType().getValue())
					// this item type code is used for mapping
					.localItemTypeCode(almaItem.getItemData().getPhysicalMaterialType().getValue())
					.canonicalItemType(null)
					.deleted(null)
					.suppressed(derivedSuppression)
					// The system the item came from, as opposed to owningContext, which is
					// overwritten with the agency's Host LMS once the location resolves. When
					// the location does not resolve there is no agency and so no owning
					// context, and this is then the only record of where the item came from.
					.sourceHostLmsCode(getHostLmsCode())
					.owningContext(getHostLms().getCode())
					// Need to query loans API for this
					.availableDate(null)
					.rawVolumeStatement(null)
					.parsedVolumeStatement(null)
					.build();
			});
	}

	private ItemStatus deriveItemStatus(AlmaItemData almaItem) {
		// Extract base status, default to 0
		// Note: this means that "item not in place" is considered UNKNOWN
		// Should it be considered unavailable? We can possibly build in the description and process type also
		String extracted_base_status = almaItem.getBaseStatus() != null ? almaItem.getBaseStatus().getValue() : "0";

		return switch ( extracted_base_status ) {
			case "1" -> new ItemStatus(ItemStatusCode.AVAILABLE);  // "1"==Item In Place
			case "2" -> new ItemStatus(ItemStatusCode.CHECKED_OUT);  // "2"=Loaned
			default -> new ItemStatus(ItemStatusCode.UNKNOWN);
		};
	}

	private HostLmsItem deriveItemStatusFromProcessType(HostLmsItem hostLmsItem, AlmaItemData almaItem) {
		// /conf/code-tables/PROCESSTYPE
		String extracted_process_type = Optional.ofNullable(almaItem.getProcess_type())
		.map(CodeValuePair::getValue)
		.filter(value -> !value.isEmpty())
		.orElse(null);

		String extracted_base_status = Optional.ofNullable(almaItem.getBaseStatus())
			.map(CodeValuePair::getValue)
			.filter(value -> !value.isEmpty())
			.orElse(null);

		// If the base status is 1 then we can assume the item is available
		if ( extracted_process_type == null && Objects.equals(extracted_base_status, "1")) {
			hostLmsItem.setStatus(HostLmsItem.ITEM_AVAILABLE);
			hostLmsItem.setRawStatus(extracted_base_status);
			return hostLmsItem;
		}

		// the base status has only two values, to get more detail we need to look at the process type
		if ( extracted_process_type != null ) {
			final var mappedStatus = switch ( extracted_process_type ) {
				case "LOAN" -> HostLmsItem.ITEM_LOANED;
				case "TRANSIT" -> HostLmsItem.ITEM_TRANSIT;
				case "MISSING" -> HostLmsItem.ITEM_MISSING;
				case "HOLDSHELF" -> HostLmsItem.ITEM_ON_HOLDSHELF;
				case "REQUESTED" -> HostLmsItem.ITEM_REQUESTED;
				default -> extracted_process_type;
			};

			hostLmsItem.setStatus(mappedStatus);
			hostLmsItem.setRawStatus(extracted_process_type);
			return hostLmsItem;
		}

		// fall back to the general base status
		if (Objects.equals(extracted_base_status, "2")) {
			hostLmsItem.setStatus(HostLmsItem.ITEM_LOANED);
			hostLmsItem.setRawStatus(extracted_base_status);
			return hostLmsItem;
		}

		// we ran out of options
		log.warn("Unable to derive item status from process type {} and base status {}", extracted_process_type, extracted_base_status);
		hostLmsItem.setStatus(extracted_base_status);
		hostLmsItem.setRawStatus(extracted_base_status);
		return hostLmsItem;
	}

	/**
	 * The DCB Location standing for an Alma library.
	 * <p>
	 * Was named checkLibraryCodeInDCBLocationRegistry, which described what its comment
	 * wished it did rather than what it does - it builds a transient Location and
	 * checks nothing. Recording locations DCB has not seen before is
	 * LocationService.memoize's job, reached from the availability path, so the name is
	 * now what the method is.
	 */
	private Location locationForLibraryCode(String almaLibraryCode) {
		return Location.builder()
			.id(UUIDUtils.generateLocationId(hostLms.getCode(), almaLibraryCode))
			.code(almaLibraryCode)
			.name(almaLibraryCode)
			.hostSystem((DataHostLms)hostLms)
			.type("Library")
			.build();
	}

	public LocalRequest mapAlmaRequestToLocalRequest(AlmaRequestResponse response) {
		return LocalRequest.builder()
			.localId(response.getRequestId())
			.localStatus(checkHoldStatus(response.getRequestStatus()))
			.rawLocalStatus(response.getRequestStatus())
			.requestedItemId(response.getItemId())
			.requestedItemBarcode(response.getItemBarcode())
			.build();
	}

	public String getHostLmsCode() {
		String result = hostLms.getCode();
		if ( result == null ) {
			log.warn("getCode from hostLms returned NULL : {}",hostLms);
		}
		return result;
	}

	// The location is set up during onboarding: creating it here needed an API key with configuration write access
	private Mono<AlmaLocation> requireVirtualLocation(String libraryCode) {
		final String locationCode = config.getVirtualItemLocationCode();

		return client.retrieveLocation(libraryCode, locationCode)
			.map(location -> {
				location.setLibraryCode(libraryCode);
				return location;
			})
			.onErrorMap(error -> new IllegalStateException("Alma location " + locationCode
				+ " is not available in library " + libraryCode + " on " + getHostLmsCode()
				+ "; create it in Alma before enabling borrowing", error));
	}

@Override
public Mono<HostLmsItem> getItemByBarcode(String barcode) {
	log.debug("Fetching Alma item by barcode: {}", barcode);

	return client.retrieveItemBarcodeOnly(barcode)
		.flatMap(item -> {
			if (item == null || item.getItemData() == null) {
				log.warn("No item found in Alma for barcode: {}", barcode);
				return Mono.empty();
			}

			final AlmaItemData aid = item.getItemData();
			final String bibId = item.getBibData() != null ? item.getBibData().getMmsId() : null;
			final String holdingId = item.getHoldingData() != null ? item.getHoldingData().getHoldingId() : null;
			final String itemId = aid.getPid();

			return client.retrieveItemRequests(bibId, holdingId, itemId)
				.map(requests -> (requests.getRecordCount() != null) ? requests.getRecordCount() : 0)
				.doOnError(e -> log.warn("Failed to retrieve hold count for Alma item barcode {}. Defaulting to 0. Error: {}", barcode, e.getMessage()))
				.onErrorReturn(0)
				.map(holdCount -> {

					var returnHostLmsItem = HostLmsItem.builder()
						.localId(itemId)
						.barcode(aid.getBarcode())
						.rawStatus(aid.getBaseStatus().getDesc())
						.bibId(bibId)
						.holdingId(holdingId)
						.holdCount(holdCount)
						.build();

					return deriveItemStatusFromProcessType(returnHostLmsItem, item.getItemData());
				});
		})
		.doOnError(e -> log.error("Failed to fetch item by barcode {} from Alma: {}", barcode, e.getMessage()));
}
	private Mono<AlmaUser> checkAndUpdateExpiryIfNeeded(AlmaUser almaUser) {
		final var patronExpiryDate = parseAlmaExpiryDate(almaUser.getExpirationDate(), almaUser.getPrimary_id());

		if (patronExpiryDate == null) {
			log.warn("Alma Patron {} has no usable expiration date. Cannot check expiry.", almaUser.getPrimary_id());
			return Mono.just(almaUser);
		}

		final var now = LocalDate.now(ZoneOffset.UTC);
		final var expiryThreshold = now.plusDays(30);

		if (!patronExpiryDate.isBefore(expiryThreshold)) {
			return Mono.just(almaUser);
		}

		final var newExpiryDate = now.plusDays(120);
		// Alma doesn't always like the "Z", but does accept it on the way back in.
		final String newExpirationDateStr = newExpiryDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "Z";

		almaUser.setExpirationDate(newExpirationDateStr);
		almaUser.setStatus(CodeValuePair.builder().value("ACTIVE").build());

		return client.updateUserDetails(almaUser.getPrimary_id(), almaUser)
			.map(updatedUser -> {
				log.debug("Successfully extended expiry date for Alma virtual patron {} to {}",
					almaUser.getPrimary_id(), newExpirationDateStr);
				return updatedUser;
			})
			.onErrorResume(e -> {
				log.error("ERROR: Failed to extend expiry date for Alma virtual patron {}. Continuing with existing expiry. Error: {}",
					almaUser.getPrimary_id(), e.getMessage());
				return Mono.just(almaUser);
			});
	}
}

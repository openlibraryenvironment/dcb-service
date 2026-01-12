package org.olf.dcb.core.interaction.alma;

import static io.micrometer.common.util.StringUtils.isBlank;
import static org.olf.dcb.core.model.FunctionalSettingType.VIRTUAL_PATRON_NAMES_VISIBLE;
import static org.olf.dcb.core.model.WorkflowConstants.EXPEDITED_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.ReactorUtils.raiseError;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.NotImplementedException;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
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
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.PreventRenewalCommand;
import org.olf.dcb.core.interaction.VirtualPatronNotFound;
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
import org.zalando.problem.DefaultProblem;
import org.zalando.problem.Problem;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.AlmaLibraryResponse;
import services.k_int.interaction.alma.AlmaLocation;
import services.k_int.interaction.alma.types.AlmaBib;
import services.k_int.interaction.alma.types.AlmaGroupedLocationResponse;
import services.k_int.interaction.alma.types.AlmaUser;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.UserIdentifier;
import services.k_int.interaction.alma.types.WithAttr;
import services.k_int.interaction.alma.types.error.AlmaError;
import services.k_int.interaction.alma.types.error.AlmaErrorResponse;
import services.k_int.interaction.alma.types.error.AlmaException;
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
	
	@Override
	public Mono<List<Item>> getItems(BibRecord bib) {
		// /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/
		return client.retrieveHoldingsList(bib.getSourceRecordId())
			.flatMapMany(holdingResponse -> {
				List<AlmaHolding> holdings = holdingResponse.getHoldings();
				if (holdings == null || holdings.isEmpty()) {
					return Flux.empty();
				}
				return Flux.fromIterable(holdings)
					.flatMap(holding -> client.retrieveItemsList(bib.getSourceRecordId(), holding.getHoldingId())
						.onErrorResume(e -> {
							log.warn("Failed to fetch items for holding ID {}: {}", holding.getHoldingId(), e.getMessage());
							return Mono.empty(); // Skip this holding on error
						}));
			})
			.flatMap(itemListResponse -> {
				List<AlmaItem> items = itemListResponse.getItems();
				if (items == null || items.isEmpty()) {
					return Flux.empty();
				}
				return Flux.fromIterable(items);
			})
			.flatMap(this::mapAlmaItemToDCBItem)
			.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, getHostLmsCode()))
			.flatMap(materialTypeToItemTypeMappingService::enrichItemWithMappedItemType)
			.onErrorContinue((throwable, item) -> {
				log.warn("Mapping error for item {}: {}", item, throwable.getMessage());
			})
			.collectList();
	}

	// The minimum DCB should give Alma
	private record DCBHold(String localPatronId, String localItemId, Location pickupLocation, String note,
		String supplyingLocalItemLocation, String activeWorkflow) {}

	// This is the minimum we should know to place a hold in Alma (V1)
	private record MinimumAlmaHold(String localPatronId, String localItemId, String pickupLibraryCode, String comment) {}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(hold -> EXPEDITED_WORKFLOW.equals(hold.activeWorkflow())
				? resolveLibraryFromLocationRecord(hold) : getDcbSharingLibraryCode())
			.flatMap(lib -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), lib, p.getNote())))
			.doOnSubscribe(s -> log.info("placeHoldRequestAtSupplyingAgency patron={} item={}",
				p.getLocalPatronId(), p.getLocalItemId()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(hold -> PICKUP_ANYWHERE_WORKFLOW.equals(hold.activeWorkflow())
				? getDcbSharingLibraryCode() : resolveLibraryFromLocationRecord(hold))
			.flatMap(lib -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), lib, p.getNote())))
			.doOnSubscribe(s -> log.info("placeHoldRequestAtBorrowingAgency patron={} item={}",
				p.getLocalPatronId(), p.getLocalItemId()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(this::resolveLibraryFromLocationRecord)
			.flatMap(lib -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), lib, p.getNote())))
			.doOnSubscribe(s -> log.info("placeHoldRequestAtPickupAgency patron={} item={}",
				p.getLocalPatronId(), p.getLocalItemId()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(this::resolveLibraryFromLocationRecord)
			.flatMap(lib -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), lib, p.getNote())))
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

	private Mono<LocalRequest> submitLibraryHold(MinimumAlmaHold hold) {
		final var payload = AlmaRequest.builder()
			.requestType("HOLD")
			.pickupLocationType("LIBRARY")
			.pickupLocationLibrary(hold.pickupLibraryCode())
			.comment(hold.comment())
			.build();

		return client.createUserRequest(hold.localPatronId(), hold.localItemId(), payload)
			.doOnSubscribe(s -> log.info("Submitting HOLD patron={} item={} pickupLibrary={}",
				hold.localPatronId(), hold.localItemId(), hold.pickupLibraryCode()))
			.doOnError(this::logAlmaProblemDetails)
			.map(this::mapAlmaRequestToLocalRequest)
			.switchIfEmpty(raiseError(new AlmaHostLmsClientException(
				"Empty Alma response creating hold for patron "+hold.localPatronId()+" / item "+hold.localItemId())));
	}

	private void logAlmaProblemDetails(Throwable e) {
		final var errs = extractAlmaErrors(e);
		if (errs != null && errs.getErrorList() != null && errs.getErrorList().getError() != null) {
			errs.getErrorList().getError().forEach(err ->
				log.error("Alma error code={} message={}", err.getErrorCode(), err.getErrorMessage()));
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

	public Mono<AlmaLocation> fetchLocationByLocationCode(String locationCode) {
		return fetchLocations()
			.flatMap(response -> {
				List<AlmaLocation> locations = response.getLocations();
				if (locations == null || locations.isEmpty()) {
					throw Problem.builder()
						.withTitle("No locations available in Alma configuration")
						.withDetail("No locations were returned from Alma for host LMS: " + getHostLmsCode())
						.with("hostLmsCode", getHostLmsCode())
						.build();
				}

				if (locationCode == null || locationCode.isBlank()) {
					throw Problem.builder()
						.withTitle("Location code is blank or null " + locationCode)
						.withDetail("Could not find any Alma location with the exact code.")
						.with("locationCode", locationCode)
						.with("hostLmsCode", getHostLmsCode())
						.build();
				}

				List<AlmaLocation> matchingLocations = locations.stream()
					.filter(loc -> locationCode.equals(loc.getCode()))
					.toList();

				if (matchingLocations.isEmpty()) {
					throw Problem.builder()
						.withTitle("No matching location found for location code: " + locationCode)
						.withDetail("Could not find any Alma location with the exact code.")
						.with("locationCode", locationCode)
						.with("hostLmsCode", getHostLmsCode())
						.build();
				}

				if (matchingLocations.size() > 1) {
					List<String> matchedNames = matchingLocations.stream()
						.map(AlmaLocation::getName)
						.toList();

					throw Problem.builder()
						.withTitle("Multiple locations found for location code: " + locationCode)
						.withDetail("Expected a single matching location, but found multiple.")
						.with("locationCode", locationCode)
						.with("hostLmsCode", getHostLmsCode())
						.with("matchingLocationNames", matchedNames)
						.build();
				}

				if ("CLOSED".equalsIgnoreCase(matchingLocations.get(0).getType().getValue())) {
					log.warn("The ALMA location corresponding to the item location code is unavailable. Falling back to our default location.");
					// Use default pickup location code to fetch the default location
					return fetchLocationByLocationCode(config.getDefaultPatronLocationCode("GTMAIN"));
				}
				return Mono.just(matchingLocations.get(0));
			});
	}

	public Mono<AlmaLocation> fetchLocationByCircDeskCode(String circDeskCode) {
		return fetchLocations()
			.flatMap(response -> {
				List<AlmaLocation> locations = response.getLocations();
				if (locations == null || locations.isEmpty()) {
					throw Problem.builder()
						.withTitle("No locations available in Alma configuration")
						.withDetail("No locations were returned from Alma for host LMS: " + getHostLmsCode())
						.with("hostLmsCode", getHostLmsCode())
						.build();
				}

				List<AlmaLocation> matchingLocations = locations.stream()
					.filter(loc -> loc.getCircDesk() != null &&
						loc.getCircDesk().stream()
							.anyMatch(cd -> circDeskCode.equals(cd.getCircDeskCode())))
					.toList();

				if (matchingLocations.isEmpty()) {
					throw Problem.builder()
						.withTitle("No matching locations found for circ desk code: " + circDeskCode)
						.withDetail("Could not find any Alma locations with a circ desk matching the given code.")
						.with("circDeskCode", circDeskCode)
						.with("hostLmsCode", getHostLmsCode())
						.build();
				}

				if (matchingLocations.size() > 1) {
					log.info("Multiple locations have been found for this circulation desk. We will use the first one that is OPEN: failing that we will default to the default location.");
					// Try and find an open location in the matching location
					Optional<AlmaLocation> openLocation = matchingLocations.stream()
						.filter(loc -> loc.getType() != null && "OPEN".equalsIgnoreCase(loc.getType().getValue()))
						.findFirst();

					if (openLocation.isPresent()) {
						AlmaLocation selectedLocation = openLocation.get();
						log.info("Prioritizing and selecting 'OPEN' location: {} at library {}", selectedLocation.getCode(), selectedLocation.getLibraryName());
						return Mono.just(selectedLocation);
					} else {
						// Is a remote location available at this library- if so default to that
						Optional<AlmaLocation> remoteLocation = matchingLocations.stream()
							.filter(loc -> loc.getType() != null && "REMOTE".equalsIgnoreCase(loc.getType().getValue()))
							.findFirst();
						if (remoteLocation.isPresent())
						{
							log.info("Returning a REMOTE location because there are no OPEN locations.");
							return Mono.just(remoteLocation.get());
						}
						else
						{
							log.warn("No 'OPEN' locations found among matches. Falling back to the default location for this system.");
							return fetchLocationByLocationCode(config.getDefaultPatronLocationCode("GTMAIN"));
						}

					}
					// Commenting out because I think we may be able to safely handle this situation, but I could be horribly wrong
					//					throw Problem.builder()
					//						.withTitle("Multiple locations found for circ desk code: " + circDeskCode)
					//						.withDetail("Expected a single matching location, but found multiple.")
					//						.with("circDeskCode", circDeskCode)
					//						.with("hostLmsCode", getHostLmsCode())
					//						.with("matchingLocationCodes", matchedLocationCodes)
					//						.build();
					//				}
				}
				return Mono.just(matchingLocations.get(0));
			});
	}

	public Mono<AlmaLocation> fetchLocationByLibraryCode(String libraryCode) {
		return client.retrieveLocations(libraryCode)
			.flatMap(response -> {
				List<AlmaLocation> locations = response.getLocations();
				if (locations == null || locations.isEmpty()) {
					throw Problem.builder()
						.withTitle("No locations available for library: " + libraryCode)
						.withDetail("No locations were returned from Alma for library code: " + libraryCode)
						.with("libraryCode", libraryCode)
						.with("hostLmsCode", getHostLmsCode())
						.build();
				}

				if (libraryCode == null || libraryCode.isBlank()) {
					throw Problem.builder()
						.withTitle("Library code is blank or null")
						.withDetail("Cannot search for locations with a blank or null library code.")
						.with("libraryCode", libraryCode)
						.with("hostLmsCode", getHostLmsCode())
						.build();
				}

				// First, try to find an OPEN location
				Optional<AlmaLocation> openLocation = locations.stream()
					.filter(loc -> loc.getType() != null && "OPEN".equalsIgnoreCase(loc.getType().getValue()))
					.findFirst();

				if (openLocation.isPresent()) {
					AlmaLocation selectedLocation = openLocation.get();
					// Set the library code and name on the location for consistency
					selectedLocation.setLibraryCode(libraryCode);
					log.info("Found OPEN location: {} for library {}", selectedLocation.getCode(), libraryCode);
					return Mono.just(selectedLocation);
				}

				// If no OPEN location found, try REMOTE as fallback
				Optional<AlmaLocation> remoteLocation = locations.stream()
					.filter(loc -> loc.getType() != null && "REMOTE".equalsIgnoreCase(loc.getType().getValue()))
					.findFirst();

				if (remoteLocation.isPresent()) {
					AlmaLocation selectedLocation = remoteLocation.get();
					selectedLocation.setLibraryCode(libraryCode);
					log.info("No OPEN location found, using REMOTE location: {} for library {}",
						selectedLocation.getCode(), libraryCode);
					return Mono.just(selectedLocation);
				}

				// If neither OPEN nor REMOTE found, fall back to default location
				log.warn("No OPEN or REMOTE locations found for library {}. Falling back to default location.", libraryCode);
				return fetchLocationByLocationCode(config.getDefaultPatronLocationCode("GTMAIN"));
			})
			.onErrorResume(e -> {
				log.error("Failed to fetch locations for library code {}: {}", libraryCode, e.getMessage());
				// Fall back to default location on any error
				return fetchLocationByLocationCode(config.getDefaultPatronLocationCode("GTMAIN"));
			});
	}

	public Mono<AlmaGroupedLocationResponse> fetchLocations() {
		return client.retrieveLibraries()
			.flatMapMany(librariesResponse -> {
				List<AlmaLibraryResponse> libraries = librariesResponse.getLibraries();
				log.info("Libraries for Alma are {}", libraries);
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

	private Mono<AlmaLocation> getSystemDependentPickupLocationCode(Location pickupLocation) {

		if (pickupLocation.getHostSystem() == null || pickupLocation.getHostSystem().getId() == null) {
			throw new IllegalArgumentException("Pickup location and its host system ID must not be null.");
		}

		final var pickupLocationHostLmsId = pickupLocation.getHostSystem().getId();
		return hostLmsService.findById(pickupLocationHostLmsId)
			.map(hostLms -> {

				final var pickupLocationLmsClientClass = hostLms.getLmsClientClass();

				if (pickupLocationLmsClientClass == null || pickupLocationLmsClientClass.isBlank()) {
					throw new IllegalStateException("LMS client class is null or empty for host LMS with ID: " + pickupLocationHostLmsId);
				}

				final var pickupLocationCircuationDesk = config.getPickupCircDesk("DEFAULT_CIRC_DESK");
				final var pickupLocationCode = pickupLocation.getCode();

				final String systemDependentPickupLocationCode = pickupLocationLmsClientClass.equals("org.olf.dcb.core.interaction.alma.AlmaHostLmsClient")
					? pickupLocationCode : pickupLocationCircuationDesk;

				return systemDependentPickupLocationCode;
			})
			.flatMap(this::fetchLocationByCircDeskCode);
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
			.map(this::almaUserToPatron);
	}

	@Override
	public Mono<Patron> getPatronByUsername(String localUsername) {
		return client.getUserDetails(localUsername)
			.map(this::almaUserToPatron);
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		log.info("Finding virtual patron {}", patron);

		final var uniqueId = getValueOrNull(patron, org.olf.dcb.core.model.Patron::determineUniqueId);

		if (uniqueId == null) {
			return Mono.error(new IllegalArgumentException("Unable to find uniqueId for virtual patron"));
		}

		// this relies on implementation relies on alma finding the user by our uniqueId
		// if we have created a user with a user identifier correctly we should be good
		// the user will need to have the user identifier enabled
		return client.getUserDetails(uniqueId)
			.doOnNext(almaUser -> log.info("Found virtual patron with uniqueId: {}", uniqueId))
			.map(this::almaUserToPatron)
			.onErrorResume(e -> {
				if (isVirtualPatronNotFoundError(e)) {
					throw createVirtualPatronNotFoundException(uniqueId, e);
				}
				return Mono.error(e);
			});
	}

	private static final String VIRTUAL_PATRON_NOT_FOUND_ERROR_CODE = "401861";

	private boolean isVirtualPatronNotFoundError(Throwable e) {
		AlmaErrorResponse errorResponse = extractAlmaErrors(e);

		if (errorResponse == null || errorResponse.getErrorList() == null || errorResponse.getErrorList().getError() == null) {
			log.info("No AlmaErrorResponse or error list present");
			return false;
		}

		for (AlmaError error : errorResponse.getErrorList().getError()) {
			log.info("Checking Alma error: code={}, message={}", error.getErrorCode(), error.getErrorMessage());
			if (VIRTUAL_PATRON_NOT_FOUND_ERROR_CODE.equals(error.getErrorCode())) {
				return true;
			}
		}

		return false;
	}

	public static AlmaErrorResponse extractAlmaErrors(Throwable e) {
		if (e instanceof AlmaException almaException) {
			try {
				return almaException.getErrorResponse();
			} catch (Exception ex) {
				log.error("Failed to get AlmaErrorResponse from AlmaException", ex);
			}
		}

		if (e instanceof DefaultProblem defaultProblem) {
			Object rawError = defaultProblem.getParameters().get("Alma Error response");

			if (rawError instanceof AlmaErrorResponse response) {
				return response; // Already deserialized
			}

			if (rawError instanceof Map) {
				try {
					ObjectMapper mapper = new ObjectMapper();
					AlmaErrorResponse response = mapper.convertValue(rawError, AlmaErrorResponse.class);
					return response;
				} catch (Exception ex) {
					log.error("Failed to convert map to AlmaErrorResponse", ex);
				}
			}

			if (rawError instanceof String json) {
				try {
					ObjectMapper mapper = new ObjectMapper();
					return mapper.readValue(json, AlmaErrorResponse.class);
				} catch (Exception ex) {
					log.error("Failed to parse AlmaErrorResponse from JSON string", ex);
				}
			}
		}

		return null;
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
				log.info("Attempting to create a patron for Alma with Patron: {}, alma user: {} and user identifiers {}. First name is {}, last name is {}", patron, almaUser, userIdentifiers, firstName, lastName);

				return determinePatronType(patron)
					.flatMap(patronType -> {
						almaUser.setUser_group(CodeValuePair.builder().value(patronType).build());

						return Mono.from(client.createUser(almaUser))
							.flatMap(returnedUser -> {
								log.info("Created alma user {}", returnedUser);
								return Mono.just(returnedUser.getPrimary_id());
							});
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
		log.info("Identifiers {}", identifiers);
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
			//.primary_id(externalId) // Workaround: to be removed once we understand where the primary_id is being lost
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
		log.info("Alma cancellation is WIP for {} cancelHoldRequest({})", getHostLms().getName(), parameters);
		// We may also need to set the status to CANCELLED

		final var userId = getValueOrNull(parameters, CancelHoldRequestParameters::getPatronId);
		final var localRequestId = getValueOrNull(parameters, CancelHoldRequestParameters::getLocalRequestId);

		// WIP implementation of cancellation.
		// We could support supplying a reason here but it has to be limited to Alma's valid RequestCancellationReasons
		return client.cancelUserRequest(userId, localRequestId)
			.thenReturn(parameters.getLocalRequestId());
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal renewal) {
		log.info("Starting direct renewal for patron {} and item {}", renewal.getLocalPatronId(), renewal.getLocalItemId());
		final String patronId = renewal.getLocalPatronId();
		final String itemId = renewal.getLocalItemId(); // Assumes this method exists

		if (itemId == null || itemId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Local Item ID is missing and required for renewal."));
		}

		// 1. Get all loans for the user.
		return client.retrieveUserLoans(patronId)
			.flatMap(userLoans -> {
				// 2. Find the loan that matches the provided item ID.
				return Mono.justOrEmpty(userLoans.getLoans().stream()
					.filter(loan -> itemId.equals(loan.getItemId()))
					.findFirst());
			})
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
		// The update is done in a 'Swap All' mode: existing fields' information will be replaced with the incoming information.
		// Incoming lists will replace existing lists.
		// Ref: https://developers.exlibrisgroup.com/alma/apis/docs/users/UFVUIC9hbG1hd3MvdjEvdXNlcnMve3VzZXJfaWR9/

		// to avoid overwriting we fetch the user first
		return client.getUserDetails(localId)
			.flatMap(returnedUser -> {
				final var almaUser = AlmaUser.builder()
					.record_type(returnedUser.getRecord_type())
					.primary_id(returnedUser.getPrimary_id())
					.first_name(returnedUser.getFirst_name())
					.last_name(returnedUser.getLast_name())
					.status(returnedUser.getStatus())
					.is_researcher(returnedUser.getIs_researcher())
					.identifiers(returnedUser.getIdentifiers())
					.external_id(returnedUser.getExternal_id())
					.account_type(returnedUser.getAccount_type())

					// update fields below
					// for now DCB only updates the patron type
					.user_group(CodeValuePair.builder().value(patronType).build())
					.build();
				return client.updateUserDetails(localId, almaUser);
			})
			.map(returnedUser -> almaUserToPatron(returnedUser));
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String barcode, String secret) {
		return client.authenticateOrRefreshUser(barcode, secret)
			.map(this::almaUserToPatron);
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

		// The patron home location appears to be null for Alma - this fallback aims to cover that while we investigate
		// As for Alma this is really the home library, so fall back to default if null
		log.info("Create item for Alma with {}", cic);
		String patronHomeLibraryCode = cic.getPatronHomeLocation() != null && !cic.getPatronHomeLocation().isBlank() ? cic.getPatronHomeLocation()  : (config.getDefaultPatronLocationCode("GENERAL"));

		AtomicReference<String> holdingId = new AtomicReference<>();

		return Mono.zip(
				fetchLocationByLocationCode(patronHomeLibraryCode),
				getMappedItemType(cic.getCanonicalItemType())
			)
			.flatMap(tuple -> {
				AlmaLocation location = tuple.getT1();
				String itemType = tuple.getT2();
				String holdingXml = buildHoldingXml(location, callNumber, holdingNote);
				AlmaItem item = buildAlmaItem(cic, location, policy, baseStatus, itemType);

				return createHolding(bibId, holdingXml)
					.flatMap(holding -> {
						holdingId.set(holding.getHoldingId());
						return client.createItem(bibId, holding.getHoldingId(), item);
					});
			})
			.map(AlmaItem::getItemData)
			.map(item -> mapToHostLmsItem(item, holdingId.get(), bibId));
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

	// This will default to the raw status when no mapping is available
	// The code of the resource sharing request status.
	// Comes from the MandatoryBorrowingWorkflowSteps or OptionalBorrowingWorkflowSteps code tables.
	private String checkHoldStatus(String status) {
		log.debug("Checking hold status: {}", status);
		return switch (status) {
			case "REJECTED", "LOCATE_FAILED", "HISTORY" -> HostLmsRequest.HOLD_CANCELLED;
			case "PENDING_APPROVAL", "READY_TO_SEND", "REQUEST_SENT",
					 "REQUEST_CREATED_BOR", "LOCATE_IN_PROCESS", "IN_PROCESS",
					 // Edge case that the item has been put in transit by staff
					 // before DCB had a chance to confirm the supplier request
					 "SHIPPED_DIGITALLY", "SHIPPED_PHYSICALLY" -> HostLmsRequest.HOLD_CONFIRMED;
			case "LOANED", "RECEIVED_DIGITALLY", "RECEIVED_PHYSICALLY" -> HostLmsRequest.HOLD_READY;
			case "DELETED" -> HostLmsRequest.HOLD_MISSING;
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
			.doOnError(e -> {
				AlmaErrorResponse almaError = extractAlmaErrors(e);
				if (almaError != null) {
					log.error("Failed to retrieve main details for Alma item {}. BibId: {}, HoldingId: {}. Alma Error: {}",
						itemId, bibId, holdingId, almaError);
				} else {
					log.error("Failed to retrieve main details for Alma item {}. BibId: {}, HoldingId: {}. Non-Alma Error: {}",
						itemId, bibId, holdingId, e.getMessage(), e);
				}
			})
			.flatMap(item -> {

				final var almaItemData = getValueOrNull(item, AlmaItem::getItemData);

				Mono<Integer> holdCountMono = client.retrieveItemRequests(bibId, holdingId, itemId)
					.map(requests -> (requests.getRecordCount() != null) ? requests.getRecordCount() : 0)
					.doOnError(e -> {
						AlmaErrorResponse almaError = extractAlmaErrors(e);
						if (almaError != null) {
							log.warn("Failed to retrieve hold count for Alma item {}. Alma Error: {}. Defaulting to 0.",
								itemId, almaError.toString());
						} else {
							log.warn("Failed to retrieve hold count for Alma item {}. Defaulting to 0. Non-Alma Error: {}",
								itemId, e.getMessage(), e);
						}
					})
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

		// Still get the old value were using for logging purposes so we know if this changes.
		// Needs to be logged while we're testing this, logging to be removed before live
		final var oldLibraryCode = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getLibraryCode);

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

				log.info("Alma: Proceeding with checkout for item {}. Corrected library: {}, Old Library: {}. Circ desk: {}",
					itemId, correctLibraryCode, oldLibraryCode, pickupLocationCircuationDesk);

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

		return client.cancelUserRequest(userId, requestId);
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
			return "";
	}

	@Override
	public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
		log.info("preventRenewalOnLoan({})", prc);

		final var itemId = prc.getItemId();
		final var itemBarcode = prc.getItemBarcode();

		final String blockMessage = "A hold has been placed on this item by a patron at the owning library. Please do not renew.";

		if (itemId == null || itemBarcode == null) {
			return Mono.error(new IllegalArgumentException("Item ID and barcode are required to prevent renewal"));
		}

		return client.retrieveItemBarcodeOnly(itemBarcode)
			.flatMap(item -> {
				AlmaItemData data = item.getItemData();

				// Essentially we do an item update here
				// This should cause a note to appear on the Item in the Alma UI/ Primo
				// So it's 'soft renewal prevention'
				// As we can't do hard renewal prevention without TOUs which as of early 2026 are v.hard/impossible to do via API
				if (data != null) {
					String currentNote = data.getFulfillmentNote();

					// Check if the note is already present to avoid duplicates
					if (currentNote == null || !currentNote.contains(blockMessage)) {

						String newNote = (currentNote == null || currentNote.isBlank())
							? blockMessage
							: currentNote + " | " + blockMessage;

						data.setFulfillmentNote(newNote);

						String mmsId = item.getBibData().getMmsId();
						String holdingId = item.getHoldingData().getHoldingId();

						return client.updateItem(mmsId, holdingId, itemId, item);
					}
				}
				// If note already exists or data is null, just return empty to signal completion
				return Mono.just(item);
			})
			.doOnSuccess(ignored -> log.info("Added fulfillment note block to item {}", itemId))
			.doOnError(e -> log.error("Failed to add block note to item {}: {}", itemId, e.getMessage()))
			.then();
	}

  @Override
  public Mono<Boolean> supplierPreflight(String borrowingAgencyCode, String supplyingAgencyCode, String canonicalItemType, String canonicalPatronType) {
    log.debug("ALMA Supplier Preflight {} {} {} {}",borrowingAgencyCode,supplyingAgencyCode,canonicalItemType,canonicalPatronType);
    return Mono.just(Boolean.TRUE);
  }

	private Patron almaUserToPatron(AlmaUser almaUser) {
		log.info("Alma user is {}", almaUser);

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

		// Extract BARCODE from user_identifiers with id_type="BARCODE"

		// AlmaUser properties
		// CodeValuePair record_type;
		// String primary_id;
		// String first_name;
		// String last_name;
		// Boolean is_researcher;
		// String link;
		// CodeValuePair gender;
		// String password;
		// CodeValuePair user_title;
		// FACULTY, STAFF, GRAD, UNDRGRD, GUEST, ACADSTAFF, ALUM, PT
		// CodeValuePair user_group;
		// CodeValuePair campus_code;
		// CodeValuePair preferred_language;
		// EXTERNAL, INTERNAL, INTEXTAUTH
		// CodeValuePair account_type;
		// String external_id;
		// ACTIVE, INACTIVE, DELETED
		// CodeValuePair status;
		// List<UserIdentifier> user_identifiers;
		localBarcodes.add(almaUser.getPrimary_id());

		return Patron.builder()
			.localId(localIds) // list
			.localNames(localNames)
			.localBarcodes(localBarcodes)
			.uniqueIds(uniqueIds)
			.localPatronType(almaUser.getUser_group().getValue())
//	.localHomeLibraryCode(almaUser.get)
			// .canonicalPatronType
			// .expiryDate
			// .localItemId
			// .localItemLocationId
			.isDeleted(Boolean.FALSE)
			.isBlocked(Boolean.FALSE)
			// .city
			// .postalCode
			// .state
			.isActive(Boolean.TRUE)
			.build();
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
			.map(AlmaRequests::getRecordCount)
			.onErrorResume(e -> {
				// If the request fails (e.g., API error), log it and default to 0.
				// This prevents one item's failure from breaking the whole list.
				log.warn("Failed to retrieve hold count for item {} (bib: {}, holding: {}): {}. Defaulting to 0.",
					itemId, bibId, holdingId, e.getMessage());
				return Mono.just(0);
			})
			.map(holdCount -> {
				// Now we have the hold count, we can build the item.
				ItemStatus derivedItemStatus = deriveItemStatus(almaItem.getItemData());
				Boolean isRequestable = (derivedItemStatus.getCode() == ItemStatusCode.AVAILABLE);

				Instant due_back_instant = almaItem.getHoldingData().getDueBackDate() != null
					? LocalDate.parse(almaItem.getHoldingData().getDueBackDate()).atStartOfDay(ZoneId.of("UTC")).toInstant()
					: null;

				// This follows the pattern seen elsewhere.. its not great.. We need to divert all these kinds of calls
				// through a service that creates missing location records in the host lms and where possible derives agency but
				// where not flags the location record as needing attention.
				Location derivedLocation = almaItem.getItemData().getLocation() != null
					? checkLibraryCodeInDCBLocationRegistry(almaItem.getItemData().getLocation().getValue())
					: null;

				Boolean derivedSuppression = ((almaItem.getBibData().getSuppressFromPublishing() != null) &&
					(almaItem.getBibData().getSuppressFromPublishing().equalsIgnoreCase("true")));

				return Item.builder()
					.localId(itemId)
					.status(derivedItemStatus)
					// In alma we need to query the Loans API to get the due date
					.dueDate(due_back_instant)
					// alma library = library of the item, location = shelving location
					.location(derivedLocation)
					.barcode(almaItem.getItemData().getBarcode())
					.callNumber(almaItem.getHoldingData().getCallNumber())
					.isRequestable(isRequestable)
					.holdCount(holdCount)
					.localBibId(bibId)
					// this item type looks to be used for auditing
					.localItemType(almaItem.getItemData().getPhysicalMaterialType().getValue())
					// this item type code is used for mapping
					.localItemTypeCode(almaItem.getItemData().getPhysicalMaterialType().getValue())
					.canonicalItemType(null)
					.deleted(null)
					.suppressed(derivedSuppression)
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

	// Alma talks about "libraries" for the location where an item "belongs" and
	// "Location" for the shelving location. These semantics don't line up neatly.
	// Whenever we see an alma library code in the context of a hostLms code we 
	// should check the DCB location repository and create a location record if
	// none exists
	private Location checkLibraryCodeInDCBLocationRegistry(String almaLibraryCode) {
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

	public HostLmsItem mapAlmaItemToHostLmsItem(AlmaItemData aid) {
		return HostLmsItem.builder()
			.build();
	}

	public String getHostLmsCode() {
		String result = hostLms.getCode();
		if ( result == null ) {
			log.warn("getCode from hostLms returned NULL : {}",hostLms);
		}
		return result;
	}
}

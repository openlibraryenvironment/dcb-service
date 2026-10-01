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
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.olf.dcb.core.ConsortiumService;
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
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.interops.ConfigType;
import org.zalando.problem.Problem;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.AlmaCircDesk;
import services.k_int.interaction.alma.AlmaCodeTable;
import services.k_int.interaction.alma.AlmaLibraryResponse;
import services.k_int.interaction.alma.AlmaLibrariesResponse;
import services.k_int.interaction.alma.AlmaLocation;
import services.k_int.interaction.alma.AlmaRequestOptions;
import services.k_int.interaction.alma.types.AlmaBib;
import services.k_int.interaction.alma.types.AlmaGroupedLocationResponse;
import services.k_int.interaction.alma.types.AlmaUser;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.UserIdentifier;
import services.k_int.interaction.alma.types.WithAttr;
import services.k_int.interaction.alma.types.holdings.AlmaHolding;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaHoldingData;
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
	private final ReferenceValueMappingService referenceValueMappingService;
	private final MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final AlmaApiClient client;
	private final AlmaClientConfig config;
	private final ConsortiumService consortiumService;

	public AlmaHostLmsClient(@Parameter HostLms hostLms,
		AlmaClientFactory almaClientFactory,
		ReferenceValueMappingService referenceValueMappingService,
		MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService,
		LocationToAgencyMappingService locationToAgencyMappingService,
	 	ConsortiumService consortiumService) {

		this.hostLms = hostLms;
		this.materialTypeToItemTypeMappingService = materialTypeToItemTypeMappingService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.config = new AlmaClientConfig(hostLms);
		this.client = almaClientFactory.createClientFor(hostLms);
		this.referenceValueMappingService = referenceValueMappingService;
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
					.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, getHostLmsCode())
						// From here the item has an agency, so a later failure can keep it and stay
						// visible; availability drops an item with no agency before anything is audited
						.flatMap(located -> materialTypeToItemTypeMappingService.enrichItemWithMappedItemType(located)
							.onErrorResume(error -> Mono.just(unmappable(located, error)))))
					.onErrorResume(error -> Mono.just(unmappableItem(almaItem, error))),
				ALMA_REQUEST_CONCURRENCY)
			.collectList();
	}

	// Keeps everything already known about the item, so it reaches the report rather than being
	// filtered out for want of an agency
	private Item unmappable(Item item, Throwable error) {
		log.warn("Could not map Alma item {} on {}", item.getLocalId(), getHostLmsCode(), error);

		return item.toBuilder()
			.isRequestable(false)
			.decisionLogEntry("Could not map this Alma item: " + error.getMessage())
			.build();
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
		String pickupDeskCode, String comment, String dcbRequestId, String localBibId,
		String localItemBarcode) {}

	private record Pickup(String libraryCode, String deskCode) {}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(hold -> EXPEDITED_WORKFLOW.equals(hold.activeWorkflow())
				? new Pickup(resolveLibraryFromLocationRecord(hold), null) : supplierPickup(hold))
			.flatMap(pickup -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), pickup.libraryCode(), pickup.deskCode(), p.getNote(),
				p.getPatronRequestId(), p.getLocalBibId(), p.getLocalItemBarcode())))
			.doOnSubscribe(s -> log.info("placeHoldRequestAtSupplyingAgency patron={} item={}",
				p.getLocalPatronId(), p.getLocalItemId()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(hold -> PICKUP_ANYWHERE_WORKFLOW.equals(hold.activeWorkflow())
				? getDcbSharingLibraryCode() : resolveLibraryFromLocationRecord(hold))
			.flatMap(lib -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), lib, null, p.getNote(), p.getPatronRequestId(),
				p.getLocalBibId(), p.getLocalItemBarcode())))
			.doOnSubscribe(s -> log.info("placeHoldRequestAtBorrowingAgency patron={} item={}",
				p.getLocalPatronId(), p.getLocalItemId()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(this::resolveLibraryFromLocationRecord)
			.flatMap(lib -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), lib, null, p.getNote(), p.getPatronRequestId(),
				p.getLocalBibId(), p.getLocalItemBarcode())))
			.doOnSubscribe(s -> log.info("placeHoldRequestAtPickupAgency patron={} item={}",
				p.getLocalPatronId(), p.getLocalItemId()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters p) {
		return validate(p)
			.map(this::resolveLibraryFromLocationRecord)
			.flatMap(lib -> submitLibraryHold(new MinimumAlmaHold(
				p.getLocalPatronId(), p.getLocalItemId(), lib, null, p.getNote(), p.getPatronRequestId(),
				p.getLocalBibId(), p.getLocalItemBarcode())))
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

	// Alma shelves an item scanned in at its hold's pickup library instead of sending it, and DCB
	// learns an item is on its way only from that transit. An Alma item's location is its owning
	// library, so an item belonging to the sharing library goes to the alternative when one is set.
	private Pickup supplierPickup(DCBHold hold) {
		final var desk = config.getSharingCircDeskCode();

		return isBlank(desk)
			? new Pickup(supplierPickupLibrary(hold), null)
			: new Pickup(getDcbSharingLibraryCode(), desk);
	}

	private String supplierPickupLibrary(DCBHold hold) {
		final var sharingLibrary = getDcbSharingLibraryCode();

		if (!sharingLibrary.equals(hold.supplyingLocalItemLocation())) {
			return sharingLibrary;
		}

		final var alternative = config.getAlternativeSharingLibraryCode();

		if (isBlank(alternative)) {
			log.warn("Item {} belongs to {}'s sharing library {}, so a scan-in will shelve it rather than "
				+ "send it: set sharing-circ-desk-code or alternative-sharing-library-code",
				hold.localItemId(), getHostLmsCode(), sharingLibrary);

			return sharingLibrary;
		}

		return alternative;
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

	/**
	 * 401129 is a fulfilment-rules answer, not an availability one: Alma found the item and
	 * refused it. The body names neither of the two things that decide it - the pickup library
	 * and the patron's user group - so Alma is asked which it was.
	 */
	private static boolean isNoItemCanFulfil(Throwable error) {
		return error instanceof AlmaApiException almaError
			&& almaError.has(AlmaApiException.Code.NO_ITEM_CAN_FULFIL);
	}

	// Two extra calls, only after a refusal. A failure to diagnose never replaces the refusal
	private Mono<AlmaRequestResponse> explainNoItemCanFulfil(MinimumAlmaHold hold, Throwable refusal) {
		final var query = new RequestOptionsQuery(hold.localPatronId(), hold.localBibId(), null,
			hold.localItemId(), hold.localItemBarcode());

		return Mono.defer(() -> checkRequestOptions(query))
			.onErrorResume(error -> Mono.just(RequestOptionsReport.failed(getHostLmsCode(), error.getMessage())))
			.flatMap(options -> Mono.error(noItemCanFulfil(hold, options, refusal)));
	}

	private static AlmaHostLmsClientException noItemCanFulfil(MinimumAlmaHold hold,
		RequestOptionsReport options, Throwable cause) {

		final var library = hold.pickupLibraryCode();
		final var refused = "Alma refused a hold on item %s for pickup at library '%s' (401129). "
			.formatted(hold.localItemId(), library);

		// Alma's answer takes no pickup location, so an offered hold points at the destination
		if (options.offersHold()) {
			return new AlmaHostLmsClientException(refused + ("Alma offers this patron a hold on this "
				+ "item, so the refusal is the pickup library: check that '%s' is a pickup location for "
				+ "the item's fulfilment unit and not a resource sharing library.").formatted(library), cause);
		}

		if (options.refusesHold()) {
			return new AlmaHostLmsClientException(refused + ("Alma offers this patron no hold on this "
				+ "item (offered: %s), so the patron's user group has no Request term of use for it.")
				.formatted(options.requestTypes().isEmpty() ? "nothing" : String.join(", ", options.requestTypes())),
				cause);
		}

		return new AlmaHostLmsClientException(refused + ("Check that '%s' is a pickup location for the "
			+ "item's fulfilment unit, and that the patron's user group has a Request term of use. "
			+ "Alma could not be asked which: %s").formatted(library, options.detail()), cause);
	}

	@Override
	public Mono<RequestOptionsReport> checkRequestOptions(RequestOptionsQuery query) {
		return itemCoordinates(query)
			.flatMap(item -> client.retrieveItemRequestOptions(item.mmsId(), item.holdingId(),
				query.localItemId(), query.localPatronId()))
			.map(this::requestOptionsReport)
			.switchIfEmpty(Mono.fromSupplier(() -> RequestOptionsReport.failed(getHostLmsCode(),
				"Could not find the bib and holding of item " + query.localItemId())))
			.onErrorResume(error -> Mono.just(RequestOptionsReport.failed(getHostLmsCode(),
				"Could not ask Alma for request options: " + error.getMessage())));
	}

	private record ItemCoordinates(String mmsId, String holdingId) {}

	// DCB records no holding id for a supplier item; the barcode lookup returns both halves
	private Mono<ItemCoordinates> itemCoordinates(RequestOptionsQuery query) {
		if (!isBlank(query.localBibId()) && !isBlank(query.localHoldingId())) {
			return Mono.just(new ItemCoordinates(query.localBibId(), query.localHoldingId()));
		}

		if (isBlank(query.localItemBarcode())) {
			return Mono.empty();
		}

		return client.retrieveItemBarcodeOnly(query.localItemBarcode())
			.mapNotNull(item -> item.getBibData() == null || item.getHoldingData() == null
				? null
				: new ItemCoordinates(item.getBibData().getMmsId(), item.getHoldingData().getHoldingId()));
	}

	private RequestOptionsReport requestOptionsReport(AlmaRequestOptions options) {
		final var types = (options.getRequestOptions() != null
				? options.getRequestOptions() : List.<AlmaRequestOptions.Option>of())
			.stream()
			.map(AlmaRequestOptions.Option::getType)
			.filter(Objects::nonNull)
			.map(CodeValuePair::getValue)
			.filter(Objects::nonNull)
			.toList();

		return new RequestOptionsReport(getHostLmsCode(), RequestOptionsReport.Status.CHECKED, null,
			types.contains("HOLD"), types, null);
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
			.pickupLocationType(hold.pickupDeskCode() != null ? "CIRCULATION_DESK" : "LIBRARY")
			.pickupLocationLibrary(hold.pickupLibraryCode())
			.pickupLocationCirculationDesk(hold.pickupDeskCode())
			.comment(holdComment(hold))
			.build();

		return client.createUserRequest(hold.localPatronId(), hold.localItemId(), payload)
			.doOnSubscribe(s -> log.info("Submitting HOLD patron={} item={} pickupLibrary={} pickupDesk={}",
				hold.localPatronId(), hold.localItemId(), hold.pickupLibraryCode(), hold.pickupDeskCode()))
			.doOnError(this::logAlmaProblemDetails)
			.onErrorResume(AlmaHostLmsClient::isNoItemCanFulfil, error -> explainNoItemCanFulfil(hold, error))
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

	// The vocabularies mappings are built from, as Alma names them
	private static final String ITEM_TYPE_CODE_TABLE = "PhysicalMaterialType";
	private static final String PATRON_TYPE_CODE_TABLE = "UserGroups";
	private static final String ITEM_POLICY_CODE_TABLE = "ItemPolicy";

	// Alma allows 50 calls a second per institution and 10 on a sandbox; two in flight stays
	// inside both, and a 429 makes the location list unreadable rather than short
	private static final int LOCATION_FETCH_CONCURRENCY = 2;

	/**
	 * Three code tables, the library list twice, and one locations call per library. Triggered by an
	 * implementer from the tools API, never on a request path.
	 */
	@Override
	public Mono<ConfigurationReport> checkConfiguration() {
		return Mono.zip(
				emptyWhenUnreadable(codeTableEntries(ITEM_TYPE_CODE_TABLE), ITEM_TYPE_CODE_TABLE),
				emptyWhenUnreadable(codeTableEntries(PATRON_TYPE_CODE_TABLE), PATRON_TYPE_CODE_TABLE),
				emptyWhenUnreadable(codeTableEntries(ITEM_POLICY_CODE_TABLE), ITEM_POLICY_CODE_TABLE),
				emptyWhenUnreadable(libraryEntries(), "libraries"),
				emptyWhenUnreadable(allLocations(), "locations"),
				emptyWhenUnreadable(resourceSharingLibraryCodes(), "libraries"),
				checkSharingDesk())
			.map(answers -> buildConfigurationReport(answers.getT1(), answers.getT2(),
				answers.getT3(), answers.getT4(), answers.getT5(), answers.getT6(), answers.getT7()))
			.onErrorResume(error -> Mono.just(ConfigurationReport
				.failed(getHostLmsCode(), "Could not read configuration from Alma: " + error.getMessage())));
	}

	@Override
	public Mono<List<ConfigurationReport.Entry>> fetchVocabulary(MappingVocabulary vocabulary) {
		return switch (vocabulary) {
			case ITEM_TYPE -> codeTableEntries(ITEM_TYPE_CODE_TABLE);
			case PATRON_TYPE -> codeTableEntries(PATRON_TYPE_CODE_TABLE);
			// A DCB Location for Alma is the owning library (see locationForLibraryCode), never a shelving location
			case LOCATION -> libraryEntries();
		};
	}

	// Location: the owning library code, as locationForLibraryCode builds it. patronType: the user
	// group code findCanonicalPatronType looks up. Item types are keyed by agency, not by this system
	@Override
	public Map<String, MappingVocabulary> readSideVocabularies() {
		return Map.of("Location", MappingVocabulary.LOCATION, "patronType", MappingVocabulary.PATRON_TYPE);
	}

	// One unreadable list leaves the rest of the report standing; the empty vocabulary says so
	private <T> Mono<List<T>> emptyWhenUnreadable(Mono<List<T>> source, String what) {
		return source.onErrorResume(error -> {
			log.warn("Could not read Alma {} at {}", what, getHostLmsCode(), error);

			return Mono.just(List.of());
		});
	}

	private Mono<List<ConfigurationReport.Entry>> codeTableEntries(String name) {
		return client.retrieveCodeTable(name)
			.map(table -> {
				final List<AlmaCodeTable.Row> rows = table.getRows() != null
					? table.getRows() : List.of();

				return rows.stream()
					.map(row -> new ConfigurationReport.Entry(
						row.getCode(), row.getDescription()))
					.toList();
			});
	}

	private Mono<List<ConfigurationReport.Entry>> libraryEntries() {
		return client.retrieveLibraries()
			.map(response -> librariesIn(response).stream()
				.map(library -> new ConfigurationReport.Entry(
					library.getCode(), library.getName()))
				.toList());
	}

	// Fails whole if any library's locations cannot be read: a partial list would report MISSING
	private Mono<List<AlmaLocation>> allLocations() {
		return client.retrieveLibraries()
			.flatMapMany(response -> Flux.fromIterable(librariesIn(response)))
			.flatMapSequential(this::locationsOf, LOCATION_FETCH_CONCURRENCY)
			.collectList();
	}

	private static List<AlmaLibraryResponse> librariesIn(AlmaLibrariesResponse response) {
		return response.getLibraries() != null ? response.getLibraries() : List.of();
	}

	private Flux<AlmaLocation> locationsOf(AlmaLibraryResponse library) {
		final var libraryCode = library.getCode();

		if (libraryCode == null
			|| (library.getNumberOfLocations() != null && library.getNumberOfLocations().getValue() == 0)) {

			return Flux.empty();
		}

		return client.retrieveLocations(libraryCode)
			.flatMapIterable(response -> response.getLocations() != null
				? response.getLocations() : List.<AlmaLocation>of())
			.doOnNext(location -> {
				location.setLibraryCode(libraryCode);
				location.setLibraryName(library.getName());
			});
	}

	private ConfigurationReport buildConfigurationReport(
		List<ConfigurationReport.Entry> itemTypes,
		List<ConfigurationReport.Entry> patronTypes,
		List<ConfigurationReport.Entry> itemPolicies,
		List<ConfigurationReport.Entry> libraries,
		List<AlmaLocation> locations,
		List<String> resourceSharingLibraryCodes,
		Optional<ConfigurationReport.Check> sharingDesk) {

		final var virtualItemLibraryCode = rawConfigValue("virtual-item-library-code");

		final var checks = new ArrayList<ConfigurationReport.Check>(List.of(
			checkPickupLibrary("sharing-library-code", libraries, resourceSharingLibraryCodes),
			checkSetting("virtual-item-library-code", virtualItemLibraryCode, libraries),
			checkVirtualItemLocation(virtualItemLibraryCode, locations),
			checkSetting("item-policy", config.getItemPolicy("BOOK"), itemPolicies),
			checkSetting("no-renew-item-policy",
				config.getNoRenewItemPolicy(AlmaClientConfig.DEFAULT_NO_RENEW_ITEM_POLICY), itemPolicies)));

		if (!isBlank(rawConfigValue("alternative-sharing-library-code"))) {
			checks.add(1, checkPickupLibrary("alternative-sharing-library-code", libraries,
				resourceSharingLibraryCodes));
		}

		sharingDesk.ifPresent(check -> checks.add(1, check));

		final var vocabularies = List.of(
			vocabulary("Item types", itemTypes),
			vocabulary("Patron types", patronTypes),
			vocabulary("Item policies", itemPolicies),
			vocabulary("Libraries (DCB locations)", libraries),
			vocabulary("Shelving locations", locations.stream()
				// Named with its library: two libraries on one tenant can both hold a MAIN
				.map(location -> new ConfigurationReport.Entry(location.getCode(),
					describeLocation(location)))
				.toList()));

		return new ConfigurationReport(getHostLmsCode(),
			ConfigurationReport.Status.CHECKED, null, checks, vocabularies);
	}

	/**
	 * Being in the library list is not enough for the library every supplier hold is sent to.
	 * <p>
	 * Alma marks its Resource Sharing Library with resource_sharing = true. That library serves
	 * Alma's own borrowing and lending workflow - its only locations are the internal
	 * OUT_RS_REQ and IN_RS_REQ - and it is not a patron pickup destination. A hold sent there
	 * comes back 401129 "No items can fulfill the submitted request", which names nothing.
	 * <p>
	 * Measured rather than assumed: it does have circulation desks, so counting those would
	 * have reported it fine.
	 */
	private ConfigurationReport.Check checkPickupLibrary(String setting,
		List<ConfigurationReport.Entry> libraries, List<String> resourceSharingLibraryCodes) {

		final var configuredValue = rawConfigValue(setting);
		final var inLibraryList = checkSetting(setting, configuredValue, libraries);

		if (inLibraryList.result() != ConfigurationReport.CheckResult.PRESENT
			|| !resourceSharingLibraryCodes.contains(configuredValue)) {

			return inLibraryList;
		}

		return new ConfigurationReport.Check(setting, configuredValue,
			ConfigurationReport.CheckResult.MISSING,
			configuredValue + " is Alma's Resource Sharing Library, which serves Alma's own borrowing "
				+ "and lending workflow and is not a patron pickup destination. Every supplier hold "
				+ "sent there is refused with 401129. Use a library patrons can collect from");
	}

	// A desk without a hold shelf cannot take a hold. Only the single-desk read carries that flag.
	private Mono<Optional<ConfigurationReport.Check>> checkSharingDesk() {
		final var setting = "sharing-circ-desk-code";
		final var desk = rawConfigValue(setting);

		if (isBlank(desk)) {
			return Mono.just(Optional.empty());
		}

		final var library = rawConfigValue("sharing-library-code");

		return client.retrieveCirculationDesk(library, desk)
			.map(found -> Boolean.TRUE.equals(found.getHasHoldShelf())
				? new ConfigurationReport.Check(setting, desk, ConfigurationReport.CheckResult.PRESENT, null)
				: new ConfigurationReport.Check(setting, desk, ConfigurationReport.CheckResult.MISSING,
					desk + " in " + library + " has no hold shelf, so Alma cannot deliver a hold to it"))
			.onErrorResume(error -> Mono.just(error instanceof AlmaApiException almaError
					&& (almaError.getStatusCode() == 400 || almaError.getStatusCode() == 404)
				? new ConfigurationReport.Check(setting, desk, ConfigurationReport.CheckResult.MISSING,
					"Not found in Alma library " + library)
				: new ConfigurationReport.Check(setting, desk, ConfigurationReport.CheckResult.UNKNOWN,
					"Could not read the desk from Alma: " + error.getMessage())))
			.map(Optional::of);
	}

	private Mono<List<String>> resourceSharingLibraryCodes() {
		return client.retrieveLibraries()
			.map(response -> librariesIn(response).stream()
				.filter(AlmaLibraryResponse::isResourceSharing)
				.map(AlmaLibraryResponse::getCode)
				.toList());
	}

	// Location codes repeat across libraries, so the code only counts inside the virtual item's library
	private ConfigurationReport.Check checkVirtualItemLocation(String virtualItemLibraryCode,
		List<AlmaLocation> locations) {

		final var setting = "virtual-item-location-code";
		final var configuredValue = rawConfigValue(setting);

		if (isBlank(configuredValue) || locations.isEmpty()) {
			return checkSetting(setting, configuredValue, List.of());
		}

		if (isBlank(virtualItemLibraryCode)) {
			return new ConfigurationReport.Check(setting, configuredValue,
				ConfigurationReport.CheckResult.UNKNOWN,
				"virtual-item-library-code is not set, so there is no library to look in");
		}

		final var inVirtualItemLibrary = locations.stream()
			.filter(location -> virtualItemLibraryCode.equals(location.getLibraryCode()))
			.map(location -> new ConfigurationReport.Entry(location.getCode(), location.getName()))
			.toList();

		if (inVirtualItemLibrary.isEmpty()) {
			return new ConfigurationReport.Check(setting, configuredValue,
				ConfigurationReport.CheckResult.MISSING,
				"Alma has no locations in library " + virtualItemLibraryCode);
		}

		return checkSetting(setting, configuredValue, inVirtualItemLibrary);
	}

	private static ConfigurationReport.Check checkSetting(String setting, String configuredValue,
		List<ConfigurationReport.Entry> knownValues) {

		return ConfigurationReport.check(setting, configuredValue, knownValues, "Alma");
	}

	private static ConfigurationReport.Vocabulary vocabulary(String name,
		List<ConfigurationReport.Entry> entries) {

		return ConfigurationReport.vocabulary(name, entries, "Alma");
	}

	private static String describeLocation(AlmaLocation location) {
		final var library = location.getLibraryName() != null
			? location.getLibraryName() : location.getLibraryCode();

		return library != null ? location.getName() + " (" + library + ")" : location.getName();
	}

	// Read from the raw config: the typed accessors throw on a missing required setting, and a
	// report has to name what is absent rather than die reading it
	private String rawConfigValue(String key) {
		final var value = getConfig().get(key);

		return value != null ? value.toString() : null;
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
			.flatMapMany(response -> Flux.fromIterable(librariesIn(response)))
			.flatMapSequential(library -> locationsOf(library)
				.onErrorResume(error -> {
					log.warn("Failed to fetch locations for library {}: {}", library.getCode(), error.getMessage());
					return Flux.empty();
				}), LOCATION_FETCH_CONCURRENCY)
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

	// Alma allows a barcode on one item per institution, and its create-item error for a clash is
	// undocumented. The virtual item must carry the supplier's barcode, because that is the label
	// staff scan, so a clash is named before anything is created. A lookup that fails for another
	// reason does not block the create: Alma is still the one that decides.
	private Mono<Void> requireBarcodeUnused(String barcode) {
		if (isBlank(barcode)) {
			return Mono.empty();
		}

		return client.retrieveItemBarcodeOnly(barcode)
			.flatMap(existing -> Mono.<Void>error(new DuplicateItemBarcodeException(getHostLmsCode(),
				getValueOrNull(existing, AlmaItem::getBibData, AlmaBib::getMmsId))))
			.onErrorResume(AlmaHostLmsClient::isNoItemForBarcode, error -> Mono.empty())
			.onErrorResume(error -> !(error instanceof DuplicateItemBarcodeException), error -> {
				log.warn("Could not check whether a virtual item's barcode is already in use at {}",
					getHostLmsCode(), error);
				return Mono.empty();
			})
			.then();
	}

	private static boolean isNoItemForBarcode(Throwable error) {
		return error instanceof AlmaApiException almaError
			&& almaError.has(AlmaApiException.Code.NO_ITEM_FOR_BARCODE);
	}

	private static boolean isItemNotFound(Throwable error) {
		return error instanceof AlmaApiException almaError && almaError.getStatusCode() == 404;
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
		final var barcodePrefix = config.getVirtualPatronBarcodePrefix();

		// Add barcode identifiers
		// WARNING: adding multiple barcodes may not be supported by Alma
		if (patron.getLocalBarcodes() != null && !patron.getLocalBarcodes().isEmpty()) {
			patron.getLocalBarcodes().stream()
				.filter(Objects::nonNull) // Guard against null barcodes in the list
				.map(barcode -> barcodePrefix + barcode)
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

	// At most 500 of the patron's loans are searched for the one being renewed or confirmed
	private static final int MAX_LOAN_PAGES = 5;

	private Mono<AlmaItemLoan> loanOfItem(String patronId, String itemId) {
		return Flux.range(0, MAX_LOAN_PAGES)
			.concatMap(page -> client.retrieveUserLoansPage(patronId, page * AlmaApiClient.LOAN_PAGE_SIZE))
			.takeUntil(page -> page.getLoans() == null || page.getLoans().size() < AlmaApiClient.LOAN_PAGE_SIZE)
			.concatMapIterable(page -> page.getLoans() != null ? page.getLoans() : List.<AlmaItemLoan>of())
			.filter(loan -> itemId.equals(loan.getItemId()))
			.next();
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal renewal) {
		log.info("Starting direct renewal for patron {} and item {}", renewal.getLocalPatronId(), renewal.getLocalItemId());
		final String patronId = renewal.getLocalPatronId();
		final String itemId = renewal.getLocalItemId();

		if (itemId == null || itemId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Local Item ID is missing and required for renewal."));
		}

		return loanOfItem(patronId, itemId)
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

	// Alma holds one patron secret, the internal password in the Ex Libris Identity Service, and a
	// consortium may call it a PIN. Any other profile would be a check Alma cannot make
	private static final Set<String> PASSWORD_AUTH_PROFILES
		= Set.of("BASIC/BARCODE+PASSWORD", "BASIC/BARCODE+PIN");

	@Override
	public Mono<Patron> patronAuth(String authProfile, String barcode, String secret) {
		if (authProfile == null || !PASSWORD_AUTH_PROFILES.contains(authProfile)) {
			return Mono.error(new IllegalStateException("Alma supports auth profiles "
				+ String.join(" and ", new TreeSet<>(PASSWORD_AUTH_PROFILES)) + ", not \"" + authProfile
				+ "\", on " + getHostLmsCode()));
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

	// Stamped on the holding DCB creates, and the only way to tell a virtual item from a real one
	static final String DCB_VIRTUAL_COLLECTION = "DCB_VIRTUAL_COLLECTION";

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		String bibId = getValueOrNull(cic, CreateItemCommand::getBibId);
		String policy = config.getItemPolicy("BOOK");
		String baseStatus = "1";
		String callNumber = DCB_VIRTUAL_COLLECTION;
		String holdingNote = "DCB Virtual holding record";

		String targetLibraryCode = config.getVirtualItemLibraryCode();

		log.info("Create item for Alma with {}. Targeting Library: {}", cic, targetLibraryCode);

		return requireBarcodeUnused(cic.getBarcode())
			.then(Mono.zip(
				requireVirtualLocation(targetLibraryCode),
				getMappedItemType(cic.getCanonicalItemType())
			))
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
			.onErrorResume(AlmaHostLmsClient::isRequestNotFound,
				error -> Mono.just(requestNoLongerInAlma(localRequestId)))
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

	/**
	 * A cancelled or fulfilled Alma request leaves the user's active list, so asking for it by id
	 * fails rather than answering with a cancelled status. Both cancellation transitions watch for
	 * MISSING or CANCELLED, and Alma produced neither - so a cancellation made in Alma reached
	 * nothing and the request ran on to the 56-day tracking cut-off.
	 */
	private static AlmaRequestResponse requestNoLongerInAlma(String localRequestId) {
		return AlmaRequestResponse.builder()
			.requestId(localRequestId)
			.requestStatus(HostLmsRequest.HOLD_MISSING)
			.build();
	}

	private static boolean isRequestNotFound(Throwable error) {
		return error instanceof AlmaApiException almaError
			&& (almaError.has(AlmaApiException.Code.REQUEST_NOT_FOUND)
				|| almaError.getStatusCode() == 404);
	}

	// A user request's request_status is only ever NOT_STARTED, IN_PROCESS or ON_HOLD_SHELF (rest_user_request.xsd)
	private String checkHoldStatus(String status) {
		if (status == null) {
			return null;
		}

		return switch (status) {
			case "NOT_STARTED", "IN_PROCESS" -> HostLmsRequest.HOLD_CONFIRMED;
			case "ON_HOLD_SHELF" -> HostLmsRequest.HOLD_READY;
			case HostLmsRequest.HOLD_MISSING -> HostLmsRequest.HOLD_MISSING;
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
			// Cleanup reads no item as already gone and an error as a failed delete. Raising for
			// an item a cataloguer had removed made every such request report a cleanup failure
			.onErrorResume(AlmaHostLmsClient::isItemNotFound, error -> Mono.empty())
			.flatMap(item -> {

				final var almaItemData = getValueOrNull(item, AlmaItem::getItemData);
				final var itemBibId = bibIdOf(item, bibId);
				final var itemHoldingId = holdingIdOf(item, holdingId);

				// One rule for both paths: an unknown count is unknown, as getItems has it. Zero here
				// reported "no holds" whenever Alma was unreachable for this one call
				Mono<Optional<Integer>> holdCountMono = client.retrieveItemRequests(itemBibId, itemHoldingId, itemId)
					.map(requests -> Optional.ofNullable(requests.getRecordCount()))
					.doOnError(e -> log.warn("Failed to retrieve hold count for Alma item {}", itemId, e))
					.onErrorResume(e -> Mono.just(Optional.empty()));

				return holdCountMono.map(holdCount -> {
					var returnHostLmsItem = HostLmsItem.builder()
						.localId(almaItemData.getPid())
						.barcode(almaItemData.getBarcode())
						.bibId(itemBibId)
						.holdingId(itemHoldingId)
						.holdCount(holdCount.orElse(null))
						.build();

					returnHostLmsItem = deriveItemStatusFromProcessType(returnHostLmsItem, almaItemData);

					return returnHostLmsItem;
				});
			});
	}

	@Override
	public Mono<String> updateItemStatus(HostLmsItem hostLmsItem, CanonicalItemState crs) {
		return switch (crs) {
			case TRANSIT -> sendTowardsPickup(hostLmsItem);
			case RECEIVED, COMPLETED -> scanInAtOwningLibrary(hostLmsItem);
			case AVAILABLE, OFFSITE, MISSING, ONHOLDSHELF -> Mono.error(new UnsupportedOperationException(
				"Alma has no item action for state " + crs));
		};
	}

	// Alma shelves an item scanned in at its hold's pickup library instead of sending it, which
	// would announce a book still on its way. Left alone, it is shelved when staff scan in the
	// real book there.
	private Mono<String> sendTowardsPickup(HostLmsItem hostLmsItem) {
		final var bibId = getValueOrNull(hostLmsItem, HostLmsItem::getBibId);
		final var holdingsId = getValueOrNull(hostLmsItem, HostLmsItem::getHoldingId);
		final var itemId = getValueOrNull(hostLmsItem, HostLmsItem::getLocalId);

		return client.retrieveItem(bibId, holdingsId, itemId)
			.flatMap(before -> holdPickupLibrary(bibIdOf(before, bibId), holdingIdOf(before, holdingsId), itemId,
					getValueOrNull(hostLmsItem, HostLmsItem::getLocalRequestId))
				.filter(pickup -> pickup.equals(libraryOf(before)))
				.doOnNext(pickup -> log.info("Not scanning in Alma item {} on {}: its hold is for pickup at its own library {}",
					itemId, getHostLmsCode(), pickup))
				.map(pickup -> "OK")
				.switchIfEmpty(Mono.defer(() -> scanIn(bibId, holdingsId, itemId, before))));
	}

	// A failed read falls back to the scan-in, which is what happened before the read existed
	private Mono<String> holdPickupLibrary(String bibId, String holdingId, String itemId, String requestId) {
		if (isBlank(requestId)) {
			return Mono.empty();
		}

		return client.retrieveItemRequests(bibId, holdingId, itemId)
			.flatMapIterable(page -> page.getRequests() != null ? page.getRequests() : List.<AlmaRequestResponse>of())
			.filter(request -> requestId.equals(request.getRequestId()))
			.next()
			.mapNotNull(AlmaRequestResponse::getPickupLocationLibrary)
			.onErrorResume(error -> {
				log.warn("Could not read the requests on Alma item {} on {}; scanning it in", itemId, getHostLmsCode(), error);
				return Mono.empty();
			});
	}

	private static String libraryOf(AlmaItem item) {
		return getValueOrNull(getValueOrNull(item, AlmaItem::getItemData), AlmaItemData::getLibrary,
			CodeValuePair::getValue);
	}

	private Mono<String> scanInAtOwningLibrary(HostLmsItem hostLmsItem) {
		final var bibId = getValueOrNull(hostLmsItem, HostLmsItem::getBibId);
		final var holdingsId = getValueOrNull(hostLmsItem, HostLmsItem::getHoldingId);
		final var itemId = getValueOrNull(hostLmsItem, HostLmsItem::getLocalId);

		return client.retrieveItem(bibId, holdingsId, itemId)
			.flatMap(before -> scanIn(bibId, holdingsId, itemId, before));
	}

	private Mono<String> scanIn(String bibId, String holdingsId, String itemId, AlmaItem before) {
		log.debug("Updating item {} with bibId {} and holdingsId {}", itemId, bibId, holdingsId);

		// The desk is configurable because a system's default desk code can differ
		final var defaultCircDesk = config.getDefaultCircDeskCode("DEFAULT_CIRC_DESK");
		final var itemBibId = bibIdOf(before, bibId);
		final var itemHoldingId = holdingIdOf(before, holdingsId);

		return client.scanIn(new ScanInQuery(itemBibId, itemHoldingId, itemId, libraryOf(before), defaultCircDesk))
			.onErrorResume(error -> scanTookEffect(itemBibId, itemHoldingId, itemId, before, error))
			.map(data -> {
				final var almaItemData = getValueOrNull(data, AlmaItem::getItemData);
				final var baseStatus = getValueOrNull(almaItemData, AlmaItemData::getBaseStatus, CodeValuePair::getValue);
				final var processType = getValueOrNull(almaItemData, AlmaItemData::getProcess_type, CodeValuePair::getValue);
				log.debug("Updated item {} with baseStatus {} and processType {}", itemId, baseStatus, processType);

				return "OK";
			});
	}

	public record ScanInQuery(String mms_id, String holding_id, String item_pid, String library, String circ_desk) {}

	// DCB records no holding id for a supplier item. Alma's item read accepts any holding segment
	// and answers with the real one; its requests and scan calls do not accept a missing one
	// The same for the bib: a caller without one gets "null" in the path, which the item read
	// tolerates and the requests call refuses
	private static String bibIdOf(AlmaItem item, String fallback) {
		final var fromItem = getValueOrNull(item, AlmaItem::getBibData, AlmaBib::getMmsId);

		return !isBlank(fromItem) ? fromItem : fallback;
	}

	private static String holdingIdOf(AlmaItem item, String fallback) {
		final var fromItem = getValueOrNull(item, AlmaItem::getHoldingData, AlmaHoldingData::getHoldingId);

		return !isBlank(fromItem) ? fromItem : fallback;
	}

	/**
	 * A scan that Alma applied but did not answer within the read timeout is a success. Observed on
	 * a sandbox: the item was on the hold shelf 52 seconds into a scan whose reply never came, and
	 * the transition went to ERROR. Changed state is the evidence; unchanged state keeps the error.
	 */
	private Mono<AlmaItem> scanTookEffect(String bibId, String holdingId, String itemId,
		AlmaItem before, Throwable error) {

		return client.retrieveItem(bibId, holdingId, itemId)
			.onErrorResume(readError -> Mono.error(error))
			.flatMap(after -> {
				if (Objects.equals(itemState(before), itemState(after))) {
					return Mono.error(error);
				}

				log.warn("Alma scan-in of item {} failed with \"{}\" but the item moved from {} to {}; treating it as done",
					itemId, error.getMessage(), itemState(before), itemState(after));

				return Mono.just(after);
			});
	}

	private static List<String> itemState(AlmaItem item) {
		final var data = getValueOrNull(item, AlmaItem::getItemData);

		return Arrays.asList(
			getValueOrNull(data, AlmaItemData::getBaseStatus, CodeValuePair::getValue),
			getValueOrNull(data, AlmaItemData::getProcess_type, CodeValuePair::getValue));
	}

	// A loan Alma created without answering in time must not be retried into "already on loan"
	private Mono<Void> loanTookEffect(String patronId, String itemId, Throwable error) {
		return loanOfItem(patronId, itemId)
			.onErrorResume(readError -> Mono.empty())
			.switchIfEmpty(Mono.error(error))
			.doOnNext(loan -> log.warn("Alma checkout of item {} failed with \"{}\" but loan {} exists for the patron; treating it as done",
				itemId, error.getMessage(), loan.getLoanId()))
			.then();
	}

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

				return client.createUserLoan(patronId, itemId, almaItemLoan)
					.then()
					.onErrorResume(error -> loanTookEffect(patronId, itemId, error));
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
		// A library's own processes can merge or overlay the virtual bib after DCB creates it,
		// and the delete below ignores Alma's warnings. So the id is deleted only while it still
		// describes the record DCB made.
		return Mono.zip(client.retrieveBib(id), holdingsOf(id))
			.flatMap(bibAndHoldings -> {
				final var reasons = reasonsToKeep(bibAndHoldings.getT1(), bibAndHoldings.getT2());

				return reasons.isEmpty()
					? Mono.from(client.deleteBibRecord(id)).then(Mono.just("OK"))
					: Mono.error(new IllegalStateException("Alma bib " + id
						+ " left in place, because it no longer looks like DCB's virtual record: "
						+ String.join("; ", reasons)));
			});
	}

	private Mono<List<AlmaHolding>> holdingsOf(String bibId) {
		return client.retrieveHoldings(bibId)
			.map(holdings -> holdings.getHoldings() != null ? holdings.getHoldings() : List.<AlmaHolding>of())
			.defaultIfEmpty(List.of());
	}

	// Bibs made before the note existed: no catalogued record carries both of that template's placeholders
	private static boolean isPreNoteVirtualBib(String marc) {
		return marc.contains("978-0-DCB-") && marc.contains("DCB Publisher");
	}

	static List<String> reasonsToKeep(AlmaBib bib, List<AlmaHolding> holdings) {
		final var reasons = new ArrayList<String>();

		if (!"true".equalsIgnoreCase(bib.getSuppressFromPublishing())) {
			reasons.add("it is not suppressed from publishing");
		}

		final var marc = bib.getAnies() != null ? String.join("", bib.getAnies()) : "";
		if (!marc.contains(AlmaXmlGenerator.VIRTUAL_BIB_NOTE) && !isPreNoteVirtualBib(marc)) {
			reasons.add("it does not carry DCB's note");
		}

		if (holdings.stream().anyMatch(holding -> !DCB_VIRTUAL_COLLECTION.equals(holding.getCall_number()))) {
			reasons.add("it has holdings DCB did not create");
		}

		return reasons;
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
		final var bibId = prc.getLocalBibId();
		final var holdingId = prc.getLocalHoldingId();
		final var itemId = prc.getItemId();

		if (isBlank(bibId) || isBlank(holdingId) || isBlank(itemId)) {
			return raiseError(new AlmaHostLmsClientException(
				"Preventing renewal in Alma needs the virtual item's bib, holding and item id"));
		}

		final var noRenewPolicy = config.getNoRenewItemPolicy(AlmaClientConfig.DEFAULT_NO_RENEW_ITEM_POLICY);

		// The guard is outside the fallback: an item DCB did not create is refused outright,
		// never written to with a note instead
		return client.retrieveItem(bibId, holdingId, itemId)
			.flatMap(item -> requireDcbVirtualItem(itemId, item))
			.flatMap(item -> denyRenewalOfItem(bibId, holdingId, itemId, item, noRenewPolicy)
				// Deferred so nothing is read back until the item has actually been written
				.then(Mono.defer(() -> confirmRenewalIsDenied(bibId, holdingId, itemId, noRenewPolicy)))
				.onErrorResume(error -> warnStaffRenewalNotPrevented(bibId, holdingId, itemId, error)));
	}

	private static final String RENEWAL_NOT_PREVENTED_NOTE
		= "DCB could not stop this item being renewed. A hold has been placed on it at the owning "
			+ "library, so please do not renew.";

	// Only the item DCB created is ever written to, so a library's own item is never touched
	private Mono<AlmaItem> requireDcbVirtualItem(String itemId, AlmaItem item) {
		final var holdingData = getValueOrNull(item, AlmaItem::getHoldingData);
		final var callNumber = holdingData != null ? holdingData.getCallNumber() : null;

		if (!DCB_VIRTUAL_COLLECTION.equals(callNumber)) {
			return raiseError(new AlmaHostLmsClientException(
				"Refusing to prevent renewal on Alma item " + itemId + " at " + getHostLmsCode()
					+ ": it does not carry the " + DCB_VIRTUAL_COLLECTION + " call number DCB creates"));
		}

		return Mono.just(item);
	}

	/**
	 * DCB could not deny renewal itself, so the only protection left is telling staff, the way
	 * the Polaris client does. The note is best effort and the original failure is re-raised
	 * either way, so the workflow still audits the request and marks it not renewable.
	 * <p>
	 * The item is read again rather than reused: a policy Alma rejected is still set on the copy
	 * in hand, and sending that back would fail for the same reason. An earlier version of this
	 * wrote to the library's own item and never removed the note; this writes only to the virtual
	 * item, which DCB deletes at FINALISED, so nothing accumulates on real holdings.
	 */
	private Mono<Void> warnStaffRenewalNotPrevented(String bibId, String holdingId, String itemId,
		Throwable cause) {

		return client.retrieveItem(bibId, holdingId, itemId)
			.flatMap(item -> requireDcbVirtualItem(itemId, item))
			.flatMap(item -> {
				final var itemData = getValueOrNull(item, AlmaItem::getItemData);

				if (itemData == null) return Mono.<AlmaItem>empty();

				itemData.setFulfillmentNote(RENEWAL_NOT_PREVENTED_NOTE);

				return client.updateItem(bibId, holdingId, itemId, item);
			})
			.doOnError(noteError -> log.error(
				"Could not warn staff on Alma item {} at {} that renewal was not prevented",
				itemId, getHostLmsCode(), noteError))
			.onErrorResume(noteError -> Mono.empty())
			.then(Mono.error(cause));
	}

	/**
	 * Alma exposes no writable renewal flag on a loan - due_date is the only field a loan PUT
	 * can change - so renewal is denied on the item, as it is for Sierra and Koha. The item
	 * policy is Alma's own override for loan rules, and DCB already sets it when it creates
	 * the item. See docs/operational/alma-setup.md for what the library configures.
	 */
	private Mono<AlmaItem> denyRenewalOfItem(String bibId, String holdingId, String itemId,
		AlmaItem item, String noRenewPolicy) {

		final var itemData = getValueOrNull(item, AlmaItem::getItemData);

		if (itemData == null) {
			return raiseError(new AlmaHostLmsClientException(
				"Alma item " + itemId + " at " + getHostLmsCode() + " was returned without item data"));
		}

		itemData.setPolicy(CodeValuePair.builder().value(noRenewPolicy).build());

		return client.updateItem(bibId, holdingId, itemId, item);
	}

	// Read back so a policy that did not take is an error here, rather than a renewal that
	// succeeds later. Whether the policy denies renewal rests on a loan rule DCB cannot see.
	private Mono<Void> confirmRenewalIsDenied(String bibId, String holdingId, String itemId,
		String expectedPolicy) {

		return client.retrieveItem(bibId, holdingId, itemId)
			.flatMap(item -> {
				final var policyPair = getValueOrNull(item, AlmaItem::getItemData, AlmaItemData::getPolicy);
				final var policy = policyPair != null ? policyPair.getValue() : null;

				if (!expectedPolicy.equals(policy)) {
					return raiseError(new AlmaHostLmsClientException(
						"Alma item " + itemId + " at " + getHostLmsCode() + " still has policy '" + policy
							+ "' after setting '" + expectedPolicy + "' to deny renewal"));
				}

				return Mono.<Void>empty();
			});
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
			.localHomeLibraryCode(getValueOrNull(almaUser, AlmaUser::getCampus_code, CodeValuePair::getValue))
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
			.onErrorResume(e -> {
				log.warn("Failed to retrieve hold count for item {} (bib: {}, holding: {}): {}",
					itemId, bibId, holdingId, e.getMessage());
				return Mono.just(Optional.<Integer>empty());
			})
			.map(holdCount -> {
				// Now we have the hold count, we can build the item.
				ItemStatus derivedItemStatus = deriveItemStatus(almaItem.getItemData());
				final var processType = processTypeOf(almaItem.getItemData());
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
					// Availability has always reported a count for every Alma item. An unread one stays
					// 0 here, and says so in rawDataValues and the decision log; tracking reads it as unknown
					.holdCount(holdCount.orElse(0))
					.localBibId(bibId)
					// this item type looks to be used for auditing
					.localItemType(materialType(almaItem.getItemData()))
					// this item type code is used for mapping
					.localItemTypeCode(materialType(almaItem.getItemData()))
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
					.rawDataValues(rawStatus(almaItem.getItemData(), processType))
					.rawDataValues(holdCount.isPresent() ? Map.of() : Map.of(HOLD_COUNT_KEY, HOLD_COUNT_UNREAD))
					.decisionLogEntries(unknownProcessType(processType))
					.decisionLogEntries(holdCount.isPresent() ? List.of() : List.of(HOLD_COUNT_UNREAD_LOG))
					.build();
			});
	}

	// An Alma item without a physical material type threw here, and the whole item became
	// unmappable; the mapping service already reports an absent code as unmapped
	private static String materialType(AlmaItemData itemData) {
		return getValueOrNull(itemData, AlmaItemData::getPhysicalMaterialType, CodeValuePair::getValue);
	}

	// Alma's PROCESSTYPE code table. Base status is only 0 or 1, in place or not; the process
	// type is the only record of why an item is not in place.
	private static final Set<String> KNOWN_PROCESS_TYPES = Set.of("ACQ", "CLAIM_RETURNED_LOAN",
		"HOLDSHELF", "ILL", "LOAN", "LOST_ILL", "LOST_LOAN", "LOST_LOAN_AND_PAID", "MISSING",
		"REQUESTED", "TECHNICAL", "TRANSIT", "TRANSIT_TO_REMOTE_STORAGE", "WORK_ORDER_DEPARTMENT");

	// Only a loan is CHECKED_OUT: TRANSIT covers both an item going home and one going to fill
	// another patron's hold, and ILL an item lent through Alma's own resource sharing, so both
	// stay UNAVAILABLE. An unknown process type is never read as available.
	static ItemStatus deriveItemStatus(AlmaItemData almaItem) {
		final var inPlace = "1".equals(baseStatusOf(almaItem));
		final var processType = processTypeOf(almaItem);

		if (processType == null) {
			return new ItemStatus(inPlace ? ItemStatusCode.AVAILABLE : ItemStatusCode.UNAVAILABLE);
		}

		return new ItemStatus(switch (processType) {
			case "LOAN" -> ItemStatusCode.CHECKED_OUT;
			case "REQUESTED" -> inPlace ? ItemStatusCode.AVAILABLE : ItemStatusCode.UNAVAILABLE;
			default -> ItemStatusCode.UNAVAILABLE;
		});
	}

	private static String baseStatusOf(AlmaItemData almaItem) {
		return blankToNull(getValueOrNull(almaItem, AlmaItemData::getBaseStatus, CodeValuePair::getValue));
	}

	private static String processTypeOf(AlmaItemData almaItem) {
		return blankToNull(getValueOrNull(almaItem, AlmaItemData::getProcess_type, CodeValuePair::getValue));
	}

	private static String blankToNull(String value) {
		return isBlank(value) ? null : value;
	}

	private static Map<String, String> rawStatus(AlmaItemData almaItem, String processType) {
		final var raw = new LinkedHashMap<String, String>();
		final var baseStatus = baseStatusOf(almaItem);

		if (baseStatus != null) {
			raw.put("baseStatus", baseStatus);
		}
		if (processType != null) {
			raw.put("processType", processType);
		}

		return raw;
	}

	static final String HOLD_COUNT_KEY = "holdCount";
	static final String HOLD_COUNT_UNREAD = "unread";
	static final String HOLD_COUNT_UNREAD_LOG
		= "Alma did not report this item's requests, so its hold count is shown as 0";

	private static List<String> unknownProcessType(String processType) {
		return processType == null || KNOWN_PROCESS_TYPES.contains(processType)
			? List.of()
			: List.of("Alma process type " + processType + " is not one DCB recognises, so the item is treated as unavailable");
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
				.map(requests -> Optional.ofNullable(requests.getRecordCount()))
				.doOnError(e -> log.warn("Failed to retrieve hold count for Alma item {}", itemId, e))
				.onErrorResume(e -> Mono.just(Optional.empty()))
				.map(holdCount -> {

					var returnHostLmsItem = HostLmsItem.builder()
						.localId(itemId)
						.barcode(aid.getBarcode())
						.rawStatus(getValueOrNull(aid, AlmaItemData::getBaseStatus, CodeValuePair::getDesc))
						.bibId(bibId)
						.holdingId(holdingId)
						.holdCount(holdCount.orElse(null))
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

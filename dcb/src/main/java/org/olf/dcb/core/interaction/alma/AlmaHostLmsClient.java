package org.olf.dcb.core.interaction.alma;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.net.URI;
import java.util.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang3.NotImplementedException;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.*;
import services.k_int.interaction.alma.types.holdings.AlmaHolding;
import services.k_int.interaction.alma.types.items.*;
import services.k_int.interaction.alma.types.userRequest.*;

import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import services.k_int.utils.UUIDUtils;

@Slf4j
@Prototype
public class AlmaHostLmsClient implements HostLmsClient {

	// @See https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/3234496514/ALMA+Integration

	// These are the same config keys as from FolioOaiPmhIngestSource
	// which was implemented prior to this client
	private static final HostLmsPropertyDefinition BASE_URL_SETTING
		= urlPropertyDefinition("alma-url", "Base request URL of the ALMA system", TRUE);
	private static final HostLmsPropertyDefinition API_KEY_SETTING
		= stringPropertyDefinition("apikey", "API key for this ALMA system", TRUE);

//	The item's override policy for loan rules.
//	Defines the conditions under which a request for this item can be fulfilled.
//	Possible codes are listed in 'ItemPolicy' code table.
	// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_item.xsd/?tags=POST
	private static final HostLmsPropertyDefinition ITEM_POLICY_SETTING
		= stringPropertyDefinition("item-policy", "Item policy for this ALMA system", FALSE);
	private static final HostLmsPropertyDefinition SHELF_LOCATION_SETTING
		= stringPropertyDefinition("shelf-location", "Shelf location for this ALMA system", FALSE);
	private static final HostLmsPropertyDefinition PICKUP_CIRC_DESK_SETTING
		= stringPropertyDefinition("pickup-circ-desk", "Pickup circ desk for this ALMA system", FALSE);

	private static final HostLmsPropertyDefinition PICKUP_LIBRARY_SETTING
		= stringPropertyDefinition("pickup-location-library", "Default pickup library for this ALMA system", FALSE);

	private final HostLms hostLms;

	private final HttpClient httpClient;

	private final ReferenceValueMappingService referenceValueMappingService;
	private final MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final ConversionService conversionService;
	private final LocationService locationService;
	private final AlmaApiClient client;

	private final String apiKey;
	private final URI rootUri;

	public AlmaHostLmsClient(@Parameter HostLms hostLms,
													 @Parameter("client") HttpClient httpClient,
													 AlmaClientFactory almaClientFactory,
													 ReferenceValueMappingService referenceValueMappingService, MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService, LocationToAgencyMappingService locationToAgencyMappingService,
													 ConversionService conversionService,
													 LocationService locationService) {

		this.hostLms = hostLms;
		this.httpClient = httpClient;
		this.materialTypeToItemTypeMappingService = materialTypeToItemTypeMappingService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;

		// this.consortialFolioItemMapper = consortialFolioItemMapper;
		this.client = almaClientFactory.createClientFor(hostLms);

		this.referenceValueMappingService = referenceValueMappingService;

		this.apiKey = API_KEY_SETTING.getRequiredConfigValue(hostLms);
		this.rootUri = UriBuilder.of(BASE_URL_SETTING.getRequiredConfigValue(hostLms)).build();
		this.conversionService = conversionService;
		this.locationService = locationService;
	}

	@Override
	public HostLms getHostLms() {
		return hostLms;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(
			BASE_URL_SETTING,
			API_KEY_SETTING,
			ITEM_POLICY_SETTING,
			SHELF_LOCATION_SETTING
		);
	}
	
	@Override
	public Mono<List<Item>> getItems(BibRecord bib) {
		// /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/
		return client.getHoldings(bib.getSourceRecordId())
			.flatMapMany(holdingResponse -> {
				List<AlmaHolding> holdings = holdingResponse.getHoldings();
				if (holdings == null || holdings.isEmpty()) {
					return Flux.empty();
				}
				return Flux.fromIterable(holdings)
					.flatMap(holding -> client.getAllItems(bib.getSourceRecordId(), holding.getHoldingId())
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
			.map(this::mapAlmaItemToDCBItem)
			.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, getHostLmsCode()))
			.flatMap(materialTypeToItemTypeMappingService::enrichItemWithMappedItemType)
			.onErrorContinue((throwable, item) -> {
				log.warn("Mapping error for item {}: {}", item, throwable.getMessage());
			})
			.collectList();
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtSupplyingAgency({})", parameters);
		return placeGenericAlmaRequest(parameters.getLocalBibId(),
			parameters.getLocalItemId(),
			parameters.getLocalHoldingId(),
			parameters.getLocalPatronId(),
			parameters.getPickupLocation().getCode(),
			parameters.getLocalItemBarcode());
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtBorrowingAgency({})", parameters);
		return placeGenericAlmaRequest(parameters.getLocalBibId(),
			parameters.getLocalItemId(),
			parameters.getLocalHoldingId(),
			parameters.getLocalPatronId(),
			parameters.getPickupLocation().getCode(),
			parameters.getLocalItemBarcode());
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtPickupAgency({})", parameters);
		return placeGenericAlmaRequest(parameters.getLocalBibId(),
			parameters.getLocalItemId(),
			parameters.getLocalHoldingId(),
			parameters.getLocalPatronId(),
			parameters.getPickupLocation().getCode(),
			parameters.getLocalItemBarcode());
	}

	private Mono<LocalRequest> placeGenericAlmaRequest(
		String mmsId,
		String itemId,
		String holdingId,
		String patronId,
		String pickupLocationCode,
		String itemBarcode) {

    log.debug("placeGenericAlmaRequest({},{}, {}, {},{},{})", mmsId, itemId, holdingId, patronId, pickupLocationCode,itemBarcode);

		final var pickupLocationCircuationDesk = PICKUP_CIRC_DESK_SETTING.getOptionalValueFrom(hostLms.getClientConfig(), "DEFAULT_CIRC_DESK");
		final var pickupLocationLibrary = PICKUP_LIBRARY_SETTING.getOptionalValueFrom(hostLms.getClientConfig(), "GTMAIN");

		final var almaRequest = AlmaRequest.builder()
			.pId(itemId)
			.requestType("HOLD")
			.pickupLocationType("LIBRARY")
			.pickupLocationLibrary(pickupLocationLibrary)
			.pickupLocationCirculationDesk(pickupLocationCircuationDesk)
			.comment("DCB Request")
			.build();

    return client.placeHold(patronId, almaRequest)
      .map( response -> mapAlmaRequestToLocalRequest(response, itemBarcode) )
      .switchIfEmpty(Mono.error(new AlmaHostLmsClientException("Failed to place generic hold at "+getHostLmsCode()+" for bib "+mmsId+" item "+itemId+" patron "+patronId)));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters parameters) {
		return placeGenericAlmaRequest(parameters.getLocalBibId(),
			parameters.getLocalItemId(),
			parameters.getLocalHoldingId(),
			parameters.getLocalPatronId(),
			parameters.getPickupLocation().getCode(),
			parameters.getLocalItemBarcode());
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
		// Almas API has a nice feature whereby the {userid} part of the GET /almaws/v1/users/{userid} can be any of the
		// identifiers from the AlmaUser user_identifiers list
		return client.getAlmaUserByUserId(localPatronId)
			.map(this::almaUserToPatron);
	}

	@Override
	public Mono<Patron> getPatronByIdentifier(String id) {
		// Almas API has a nice feature whereby the {userid} part of the GET /almaws/v1/users/{userid} can be any of the
		// identifiers from the AlmaUser user_identifiers list
		return client.getAlmaUserByUserId(id)
			.map(this::almaUserToPatron);
	}

	@Override
	public Mono<Patron> getPatronByUsername(String localUsername) {
		return client.getAlmaUserByUserId(localUsername)
			.map(this::almaUserToPatron);
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {

		// find user by external_id (DCB patron uniqueId)
		// this is assuming when a virtual patron is created the external_id is set with DCBs patron uniqueId
		final var uniqueId = getValueOrNull(patron, org.olf.dcb.core.model.Patron::determineUniqueId);
		final var uniqueId2 = getValueOrNull(patron, org.olf.dcb.core.model.Patron::getHomeLibraryCode);

		log.info("U1 {}, U2{}", uniqueId, uniqueId2);
		return client.getUsersByExternalId(uniqueId)
			.map(almaUserList -> {

				final var users = getValueOrNull(almaUserList, AlmaUserList::getUser);
				final var usersSize = (users != null) ? users.size() : 0;

				if (usersSize < 1) {
					log.warn("No virtual Patron found.");

					throw VirtualPatronNotFound.builder()
						.withDetail(usersSize + " records found")
						.with("uniqueId", uniqueId)
						.with("Response", almaUserList)
						.build();
				}

				if (usersSize > 1) {
					log.error("More than one virtual patron found: {}", almaUserList);

					throw MultipleVirtualPatronsFound.builder()
						.withDetail(usersSize + " records found")
						.with("uniqueId", uniqueId)
						.with("Response", almaUserList)
						.build();
				}

				final var onlyUser = users.get(0);

				return almaUserToPatron(onlyUser);
			});
	}

	// Static create patron defaults
	private static final String DEFAULT_FIRST_NAME = "DCB";
	private static final String DEFAULT_LAST_NAME = "VPATRON";
	private static final String RECORD_TYPE_PUBLIC = "PUBLIC";
	private static final String STATUS_ACTIVE = "ACTIVE";
	private static final String ACCOUNT_TYPE_EXTERNAL = "EXTERNAL";
	private static final String ID_TYPE_BARCODE = "BARCODE";

	@Override
	public Mono<String> createPatron(Patron patron) {

		final var homeIdentityLocalId = extractExternalId(patron);
		final var firstName = extractFirstName(patron);
		final var lastName = extractLastName(patron);
		final var externalId = extractExternalId(patron);
		final var primaryId = extractPrimaryId(patron);
		log.info("Alma patron creation starts with this patron {} and this homeIdentityLocalId {}, this externalId {}, and this primary ID {}", patron, homeIdentityLocalId, externalId, primaryId);

		// We need the primary ID  here to build our Alma user with it, but I don't think we have it

		List<UserIdentifier> userIdentifiers = createUserIdentifiers(patron);
		AlmaUser almaUser = buildAlmaUser(primaryId, firstName, lastName, externalId, userIdentifiers, primaryId);

		return determinePatronType(patron)
			.flatMap(patronType -> {
				almaUser.setUser_group(CodeValuePair.builder().value(patronType).build());

				return Mono.from(client.createPatron(almaUser))
					.flatMap(returnedUser -> {
						log.info("Created alma user {}", returnedUser);
						return Mono.just(homeIdentityLocalId);
					});
			});
	}

	private String extractPatronId(Patron patron) {

		if (patron.getLocalId() != null && !patron.getLocalId().isEmpty()) {
			return patron.getLocalId().get(0);
		}

		return null;
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


	private String extractPrimaryId(Patron patron) {
		if (patron.getUniqueIds() != null && !patron.getUniqueIds().isEmpty()) {
			return patron.getUniqueIds().get(0);
		}
		return null;
	}

	private List<UserIdentifier> createUserIdentifiers(Patron patron) {
		if (patron.getLocalBarcodes() != null) {
			var userIdentifiers = patron.getLocalBarcodes().stream()
				.map(value -> UserIdentifier.builder()
					.id_type(WithAttr.builder().value(ID_TYPE_BARCODE).build())
					.value(value)
					.build())
				.collect(Collectors.toList());

			userIdentifiers.add(UserIdentifier.builder()
				.id_type(WithAttr.builder().value("dcb_unique_id").build())
				.value(extractExternalId(patron)).build());

			userIdentifiers.add(UserIdentifier.builder()
				.id_type(WithAttr.builder().value("home_identity_local_id").build())
				.value(extractPatronId(patron)).build());

			return userIdentifiers;
		}
		return null;
	}

	private AlmaUser buildAlmaUser(String patronId, String firstName, String lastName,
																 String externalId, List<UserIdentifier> userIdentifiers, String primaryId) {
		log.info("Building an alma user with patronId {}, externalId {}, identifiers {}, primaryId {}", patronId, externalId, userIdentifiers, primaryId);
		return AlmaUser.builder()
			.record_type(CodeValuePair.builder().value(RECORD_TYPE_PUBLIC).build())
			.first_name(firstName)
			.last_name(lastName)
			.status(CodeValuePair.builder().value(STATUS_ACTIVE).build())
			.is_researcher(Boolean.FALSE)
			.user_identifiers(userIdentifiers)
			.external_id(externalId) // DCB Patron ID for home library system
			.primary_id(primaryId)
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

		return client.createBib(alma_bib)
			.map ( bibresult -> bibresult.getMmsId() );
	}

	@Override
	public Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters) {
		log.debug("{} cancelHoldRequest({})", getHostLms().getName(), parameters);
		return Mono.error(new NotImplementedException("Cancel hold request is not currently implemented in " + getHostLmsCode()));
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal hostLmsRenewal) {
		log.warn("Renewal is not currently implemented for {}", getHostLms().getName());
		return Mono.just(hostLmsRenewal);
	}

	@Override
	public Mono<LocalRequest> updateHoldRequest(LocalRequest localRequest) {
		log.warn("Update patron request is not currently implemented for {}", getHostLms().getName());
		return Mono.error(new NotImplementedException("Update patron request is not currently implemented in " + getHostLmsCode()));
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		// The update is done in a 'Swap All' mode: existing fields' information will be replaced with the incoming information.
		// Incoming lists will replace existing lists.
		// Ref: https://developers.exlibrisgroup.com/alma/apis/docs/users/UFVUIC9hbG1hd3MvdjEvdXNlcnMve3VzZXJfaWR9/

		// to avoid overwriting we fetch the user first
		return client.getAlmaUserByUserId(localId)
			.flatMap(returnedUser -> {
				final var almaUser = AlmaUser.builder()
					.record_type(returnedUser.getRecord_type())
					.primary_id(returnedUser.getPrimary_id())
					.first_name(returnedUser.getFirst_name())
					.last_name(returnedUser.getLast_name())
					.status(returnedUser.getStatus())
					.is_researcher(returnedUser.getIs_researcher())
					.user_identifiers(returnedUser.getUser_identifiers())
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
			.map(almaUser -> almaUserToPatron(almaUser));
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {

		String bibId = getValueOrNull(cic, CreateItemCommand::getBibId);

		// default found: '/conf/code-tables/ItemPolicy'
		String policy = ITEM_POLICY_SETTING.getOptionalValueFrom(hostLms.getClientConfig(), "BOOK");
		String shelfLocation = SHELF_LOCATION_SETTING.getOptionalValueFrom(hostLms.getClientConfig(), "GENERAL");
		String callNumber = "DCB_VIRTUAL_COLLECTION";
		String holdingNote = "DCB Virtual holding record";
		String baseStatus = "1"; // available
		String location = getValueOrNull(cic, CreateItemCommand::getLocationCode);

		String holdingxml = AlmaXmlGenerator.generateHoldingXml(
			location, shelfLocation, callNumber, holdingNote);

		AlmaItemData almaItemData = AlmaItemData.builder()
			.barcode(cic.getBarcode())
			.physicalMaterialType(CodeValuePair.builder().value(cic.getCanonicalItemType()).build())
			.policy(CodeValuePair.builder().value(policy).build())
			.baseStatus(CodeValuePair.builder().value(baseStatus).build())
			.description("DCB copy")
			.statisticsNote1("DCB item")
			.publicNote("Virtual item = created by DCB")
			.fulfillmentNote("Virtual item = created by DCB")
			.internalNote1("Virtual item = created by DCB")
			.holdingData(
				AlmaHolding.builder()
					.library(CodeValuePair.builder().value(location).build())
					.location(CodeValuePair.builder().value(shelfLocation).build())
					.build()
			)
			.build();

		AlmaItem almaItem = AlmaItem.builder().itemData(almaItemData).build();

		AtomicReference<String> holdingId = new AtomicReference<>();
		return createHolding(bibId, holdingxml)
			.flatMap(holding -> {
				holdingId.set(holding.getHoldingId());
				return client.createItem(bibId, holding.getHoldingId(), almaItem);
			})
			.map(AlmaItem::getItemData)
			.map( item -> HostLmsItem.builder()
				.localId(item.getPid())
				.barcode(item.getBarcode())
				.status(deriveItemStatus(item).getCode().name())
				.holdingId(holdingId.get())
				.bibId(bibId)
				.build() );
	}

	private Mono<AlmaHolding> createHolding(String bibId, String almaHolding) {
		return client.createHolding(bibId, almaHolding);
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
			case "REJECTED", "LOCATE_FAILED" -> HostLmsRequest.HOLD_CANCELLED;
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
		return client.getItemForPID(hostLmsItem.getBibId(), hostLmsItem.getHoldingId(), hostLmsItem.getLocalId())
			.map( item -> HostLmsItem.builder()
				.localId(item.getItemData().getPid())
				.barcode(item.getItemData().getBarcode())
				.rawStatus(item.getItemData().getBaseStatus().getValue())
				.status(deriveItemStatus(item.getItemData()).getCode().name())
				.bibId(hostLmsItem.getBibId())
				.holdingId(hostLmsItem.getHoldingId())
				.build() );
	}

	@Override
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs, String localRequestId) {
		// We need a way to let an alma system know that a supplier has put the item in transit
		// updating an item status isn't going to work here so we need to do something else

		// until we understand alma better we pass through here so we can progress the DCB workflow

		return Mono.just("OK");
	}

	@Override
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkoutItemCommand) {
		final var patronId = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getPatronId);
		final var requestId = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getLocalRequestId);
		final var pickupLocationCircuationDesk = PICKUP_CIRC_DESK_SETTING.getOptionalValueFrom(hostLms.getClientConfig(), "DEFAULT_CIRC_DESK");
		final var libraryCode = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getLibraryCode);
		final var itemId = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getItemId);

		log.info("Checking out item {} to patron {} with request id {} and pickup location {} and library code {}",
			itemId, patronId, requestId, pickupLocationCircuationDesk, libraryCode);

		AlmaItemLoan almaItemLoan = AlmaItemLoan.builder().circDesk(CodeValuePair.builder().value(pickupLocationCircuationDesk).build())
			.returnCircDesk(CodeValuePair.builder().value(pickupLocationCircuationDesk).build())
			.library(CodeValuePair.builder().value(libraryCode).build())
			.requestId(CodeValuePair.builder().value(requestId).build())
			.build();

		return client.createUserLoan(patronId, itemId, almaItemLoan)
			.thenReturn("OK");
	}

	@Override
	public Mono<String> deleteItem(String id) {
		return Mono.error(new NotImplementedException("Delete item is not currently implemented in " + getHostLmsCode()));
	}

	@Override
	public Mono<String> deleteItem(String id, String holdingsId, String mms_id) {
		return client.deleteItem(id, holdingsId, mms_id)
			.flatMap(result -> client.deleteHolding(holdingsId, mms_id));
	}

  @Override
  public Mono<String> deleteHold(String id) {
		return Mono.error(new NotImplementedException("Delete hold is not currently implemented in " + getHostLmsCode()));
  }

	@Override
	public Mono<String> deleteHold(String userId, String requestId) {
		return client.deleteUserRequest(userId, requestId);
	}

  public Mono<String> deletePatron(String id) {
		return Mono.from(client.deleteAlmaUser(id))
			.then(Mono.just("OK"));
	}

	@Override
	public Mono<String> deleteBib(String id) {
		return Mono.from(client.deleteBib(id))
			.then(Mono.just("OK"));
	}

	@Override
	public @NonNull String getClientId() {
			return "";
	}

  @Override
  public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
    return Mono.error(new NotImplementedException("Prevent renewal on loan is not currently implemented in " + getHostLmsCode()));
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
			// .localHomeLibraryCode
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

	public Item mapAlmaItemToDCBItem(AlmaItem almaItem) {
		ItemStatus derivedItemStatus = deriveItemStatus(almaItem.getItemData());
		Boolean isRequestable = ( derivedItemStatus.getCode() == ItemStatusCode.AVAILABLE ? Boolean.TRUE : Boolean.FALSE );

		Instant due_back_instant = almaItem.getHoldingData().getDueBackDate() != null
			? LocalDate.parse(almaItem.getHoldingData().getDueBackDate()).atStartOfDay(ZoneId.of("UTC")).toInstant()
			: null ;

		// This follows the pattern seen elsewhere.. its not great.. We need to divert all these kinds of calls
		// through a service that creates missing location records in the host lms and where possible derives agency but
		// where not flags the location record as needing attention.
		Location derivedLocation = almaItem.getItemData().getLocation() != null
			? checkLibraryCodeInDCBLocationRegistry(almaItem.getItemData().getLocation().getValue())
			: null ;

		Boolean derivedSuppression = ( ( almaItem.getBibData().getSuppressFromPublishing() != null ) && ( almaItem.getBibData().getSuppressFromPublishing().equalsIgnoreCase("true") ) )
			? Boolean.TRUE
			: Boolean.FALSE;

		return Item.builder()
		  .localId(almaItem.getItemData().getPid())
		  .status(derivedItemStatus)
			// In alma we need to query the Loans API to get the due date
		  .dueDate(due_back_instant)
			// alma library = library of the item, location = shelving location
	  	.location(derivedLocation)
		  .barcode(almaItem.getItemData().getBarcode())
		  .callNumber(almaItem.getBibData().getCallNumber())
		  .isRequestable(isRequestable)
		  .holdCount(null)
		  .localBibId(almaItem.getBibData().getMmsId())
		  .localItemType(null)
		  .localItemTypeCode(almaItem.getItemData().getPhysicalMaterialType().getValue())
		  .canonicalItemType(null)
		  .deleted(null)
		  .suppressed( derivedSuppression )
		  .owningContext(getHostLms().getCode())
			// Need to query loans API for this
		  .availableDate(null)
  		.rawVolumeStatement(null)
 			.parsedVolumeStatement(null)
			.build();
	}

	private ItemStatus deriveItemStatus(AlmaItemData almaItem) {
		// Extract base status, default to 0
		String extracted_base_status = almaItem.getBaseStatus() != null ? almaItem.getBaseStatus().getValue() : "0";

		return switch ( extracted_base_status ) {
			case "1" -> new ItemStatus(ItemStatusCode.AVAILABLE);  // "1"==Item In Place
			case "2" -> new ItemStatus(ItemStatusCode.CHECKED_OUT);  // "2"=Loaned
			default -> new ItemStatus(ItemStatusCode.UNKNOWN);
		};
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

	public LocalRequest mapAlmaRequestToLocalRequest(AlmaRequestResponse response, String itemBarcode) {
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

}

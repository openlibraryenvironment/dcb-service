package org.olf.dcb.core.interaction.alma;

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
	private static final HostLmsPropertyDefinition POLICY_SETTING
		= stringPropertyDefinition("policy", "Policy for this ALMA system", TRUE);
	private static final HostLmsPropertyDefinition ITEM_POLICY_SETTING
		= stringPropertyDefinition("item-policy", "Item policy for this ALMA system", TRUE);

	private final HostLms hostLms;

	private final HttpClient httpClient;

	private final ReferenceValueMappingService referenceValueMappingService;
	private final ConversionService conversionService;
	private final LocationService locationService;
	private final AlmaApiClient client;

	private final String apiKey;
	private final URI rootUri;

	public AlmaHostLmsClient(@Parameter HostLms hostLms,
		@Parameter("client") HttpClient httpClient,
		AlmaClientFactory almaClientFactory,
		ReferenceValueMappingService referenceValueMappingService,
		ConversionService conversionService,
		LocationService locationService) {

		this.hostLms = hostLms;
		this.httpClient = httpClient;

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
			API_KEY_SETTING
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
		String pickupInstitutionCode,
		String itemBarcode) {

    log.debug("placeGenericAlmaRequest({},{}, {}, {},{},{})", mmsId, itemId, holdingId, patronId, pickupInstitutionCode,itemBarcode);

		final var almaRequest = AlmaRequest.builder()
			.mmsId(mmsId)
			.holdingId(holdingId)
			.pId(itemId)
			.requestType("HOLD")
			.pickupLocationType("INSTITUTION")
			.pickupLocationInstitution(pickupInstitutionCode)
			.pickupLocationCircuationDesk("DEFAULT_CIRC_DESK")
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

	@Override
	public Mono<String> createPatron(Patron patron) {

		String patron_id = UUID.randomUUID().toString();
		String firstname = "DCB";
		String lastname = "VPATRON";
		String external_id = null;

		if ( ( patron.getLocalNames() != null ) && ( patron.getLocalNames().size() > 0 ) ) {
			firstname = patron.getLocalNames().get(0);
			lastname = patron.getLocalNames().get(patron.getLocalNames().size()-1);
		}

		if ( ( patron.getUniqueIds() != null ) && ( patron.getUniqueIds().size() > 0 ) ) {
			external_id = patron.getUniqueIds().get(0);
		}

		List<UserIdentifier> user_identifiers = null;

		if ( patron.getLocalBarcodes() != null ) {
			user_identifiers = patron.getLocalBarcodes().stream()
				.map(value -> UserIdentifier.builder()
					.id_type( WithAttr.builder().value("BARCODE").build() )
					.value(value)
					.note(null)
					.status(null)
					.build())
				.collect(Collectors.toList());
		}

		// POST /almaws/v1/users
		AlmaUser almaUser = AlmaUser.builder()
      .primary_id(patron_id)
      .first_name(firstname)
      .last_name(lastname)
      .is_researcher(Boolean.FALSE)
			.user_identifiers(user_identifiers)
			.external_id(external_id) // DCB Patron ID for home library system
      .link("")
			// https://developers.exlibrisgroup.com/blog/Users-API-working-with-external-internal-users/
			.account_type(CodeValuePair.builder().value("EXTERNAL").build())
      // CodeValuePair status;
      // CodeValuePair gender;
      // String password;
			.build();

		return Mono.from(client.createPatron(almaUser))
			.flatMap(returnedUser -> {
				log.info("Created alma user {}",returnedUser);
				return Mono.just(patron_id);
			});
			// N.B. Can return mono.error.
	}

	@Override
	public Mono<String> createBib(Bib bib) {
		final var author = (bib.getAuthor() != null) ? bib.getAuthor() : null;
		final var title = (bib.getTitle() != null) ? bib.getTitle() : null;

		final var alma_bib = AlmaMarcXmlGenerator.createBibXml(title, author);

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
		// DCB has no means to update users via the available edge modules
		// and edge-dcb does not do this on DCB's behalf when creating the transaction
		log.warn("NOOP: updatePatron called for hostlms {} localPatronId {} localPatronType {}",
			getHostLms().getName(), localId, patronType);

		return Mono.error(new NotImplementedException("Update patron is not currently implemented in " + getHostLmsCode()));
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String barcode, String secret) {

		return client.authenticateOrRefreshUser(barcode, secret)
			.map(almaUser -> almaUserToPatron(almaUser));
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {

		String bibId = cic.getBibId();

		// Need mapping, temp values for testing
		String policy = "BOOK";
		String itemPolicy = "BOOK";
		String shelfLocation = "GENERAL";
		String callNumber = "TEST_COLLECTION";
		String holdingNote = "Test holding record";

		String holdingxml = AlmaMarcXmlGenerator.generateHoldingXml(
			cic.getLocationCode(), shelfLocation, callNumber, holdingNote);

		AlmaItemData almaItemData = AlmaItemData.builder()
			.barcode(cic.getBarcode())
			.physicalMaterialType(CodeValuePair.builder().value(cic.getCanonicalItemType()).build())
			.itemPolicy(CodeValuePair.builder().value(itemPolicy).build())
			.policy(CodeValuePair.builder().value(policy).build())
			.baseStatus(CodeValuePair.builder().value("1").build())
			.description("Test copy")
			.statisticsNote1("Test item")
			.publicNote("Test item = created by DCB")
			.fulfillmentNote("Test item = created by DCB")
			.internalNote1("Test item = created by DCB")
			.holdingData(
				AlmaHolding.builder()
					.library(CodeValuePair.builder().value(cic.getLocationCode()).build())
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
				.status(item.getBaseStatus().getValue())
				.holdingId(holdingId.get())
				.bibId(bibId)
				.build() );
	}

	private Mono<AlmaHolding> createHolding(String bibId, String almaHolding) {
		return client.createHolding(bibId, almaHolding);
	}

	@Override
	public Mono<HostLmsRequest> getRequest(String localRequestId) {
		return Mono.error(new NotImplementedException("Get patron request is not currently implemented"));
	}

	@Override
	public Mono<HostLmsItem> getItem(HostLmsItem hostLmsItem) {
		return client.getItemForPID(hostLmsItem.getBibId(), hostLmsItem.getHoldingId(), hostLmsItem.getLocalId())
			.map( item -> HostLmsItem.builder()
				.localId(item.getItemData().getPid())
				.barcode(item.getItemData().getBarcode())
				.status(item.getItemData().getBaseStatus().getValue())
				.bibId(hostLmsItem.getBibId())
				.holdingId(hostLmsItem.getHoldingId())
				.build() );
	}

	@Override
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs, String localRequestId) {
		return Mono.error(new NotImplementedException("Update item status is not currently implemented in " + getHostLmsCode()));
	}

	@Override
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkoutItemCommand) {
		return Mono.error(new NotImplementedException("Check out item to patron is not currently implemented in " + getHostLmsCode()));
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

		List<String> localIds = new ArrayList<String>();
		List<String> uniqueIds = new ArrayList<String>();
		List<String> localBarcodes = new ArrayList<String>();
		List<String> localNames = new ArrayList<String>();

		if ( almaUser.getPrimary_id() != null ) {
			localIds.add(almaUser.getPrimary_id());
			uniqueIds.add(almaUser.getPrimary_id());
		}

		if ( almaUser.getExternal_id() != null ) {
			localIds.add(almaUser.getExternal_id());
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


		return Patron.builder()
			.localId(localIds) // list
			.localNames(localNames)
			.localBarcodes(localBarcodes)
			.uniqueIds(uniqueIds)
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
		ItemStatus derivedItemStatus = deriveItemStatus(almaItem);
		Boolean isRequestable = ( derivedItemStatus.getCode() == ItemStatusCode.AVAILABLE ? Boolean.TRUE : Boolean.FALSE );

		Instant due_back_instant = almaItem.getHoldingData().getDueBackDate() != null
			? LocalDate.parse(almaItem.getHoldingData().getDueBackDate()).atStartOfDay(ZoneId.of("UTC")).toInstant()
			: null ;

		// This follows the pattern seen elsewhere.. its not great.. We need to divert all these kinds of calls
		// through a service that creates missing location records in the host lms and where possible derives agency but
		// where not flags the location record as needing attention.
		Location derivedLocation = almaItem.getItemData().getLibrary() != null
			? checkLibraryCodeInDCBLocationRegistry(almaItem.getItemData().getLibrary().getValue())
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
		  .localItemTypeCode(null)
		  .canonicalItemType(null)
		  .deleted(null)
		  .suppressed( derivedSuppression )
		  .owningContext(almaItem.getItemData().getLibrary() != null ? almaItem.getItemData().getLibrary().getValue() : null )
			// Need to query loans API for this
		  .availableDate(null)
  		.rawVolumeStatement(null)
 			.parsedVolumeStatement(null)
			.build();
	}

	private ItemStatus deriveItemStatus(AlmaItem almaItem) {
		// Extract base status, default to 0
		String extracted_base_status = almaItem.getItemData().getBaseStatus() != null ? almaItem.getItemData().getBaseStatus().getValue() : "0";

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

	public LocalRequest mapAlmaRequestToLocalRequest(AlmaRequest almaRequest, String itemBarcode) {
		return LocalRequest.builder()
			.localId(almaRequest.getRequestId())
			.localStatus(almaRequest.getRequestStatus().getValue())
			.rawLocalStatus(null)
			.requestedItemId(almaRequest.getMmsId())
			.requestedItemBarcode(itemBarcode)
			.canonicalItemType(null)
			.supplyingAgencyCode(null)
			.supplyingHostLmsCode(hostLms.getCode())
			.build();
	}

	public HostLmsItem mapAlmaItemToHostLmsItem(AlmaItemData aid) {
		return HostLmsItem.builder()
			.build();
	}

}

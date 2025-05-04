package org.olf.dcb.core.interaction.alma;

import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static services.k_int.utils.ReactorUtils.raiseError;

import java.net.URI;
import java.util.*;
import java.time.*;
import java.util.stream.Collectors;

import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.*;
import services.k_int.interaction.alma.types.items.*;

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
		= urlPropertyDefinition("base-url", "Base URL of the ALMA system", TRUE);
	private static final HostLmsPropertyDefinition API_KEY_SETTING
		= stringPropertyDefinition("apikey", "API key for this ALMA system", TRUE);

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
			.flatMap(holding -> client.getAllItems(bib.getSourceRecordId()))
			.flatMapMany(items -> Flux.fromIterable(items.getItems()))
			.map(almaItem -> mapAlmaItemToDCBItem(almaItem))
			.collectList();
	}


	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters parameters) {

		log.debug("placeHoldRequestAtSupplyingAgency({})", parameters);
		return Mono.empty();
	}


	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters) {

		log.debug("placeHoldRequestAtBorrowingAgency({})", parameters);

		return Mono.empty();
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtPickupAgency({})", parameters);
		return Mono.empty();
	}


	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters parameters) {
		return raiseError(new UnsupportedOperationException("placeHoldRequestAtLocalAgency not supported by hostlms: " + getHostLmsCode()));
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
		return Mono.empty();
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
		return Mono.empty();
	}


	@Override
	public Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters) {
		log.debug("{} cancelHoldRequest({})", getHostLms().getName(), parameters);
		return Mono.empty();
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal hostLmsRenewal) {
		log.warn("Renewal is not currently implemented for {}", getHostLms().getName());
		return Mono.just(hostLmsRenewal);
	}

	@Override
	public Mono<LocalRequest> updateHoldRequest(LocalRequest localRequest) {
		log.warn("Update patron request is not currently implemented for {}", getHostLms().getName());
		return Mono.empty();
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		// DCB has no means to update users via the available edge modules
		// and edge-dcb does not do this on DCB's behalf when creating the transaction
		log.warn("NOOP: updatePatron called for hostlms {} localPatronId {} localPatronType {}",
			getHostLms().getName(), localId, patronType);

		return Mono.empty();
	}


	@Override
	public Mono<Patron> patronAuth(String authProfile, String barcode, String secret) {
		return Mono.empty();
	}


	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		return Mono.empty();
	}


	@Override
	public Mono<HostLmsRequest> getRequest(String localRequestId) {
		return Mono.empty();
	}


	@Override
	public Mono<HostLmsItem> getItem(String localItemId, String localRequestId) {
		log.debug("getItem({}, {})", localItemId, localRequestId);

		

		return Mono.empty();
	}


	@Override
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs, String localRequestId) {
		return Mono.empty();
	}


	@Override
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkoutItemCommand) {
		return Mono.empty();
	}

	@Override
	public Mono<String> deleteItem(String id) {
		log.info("Delete virtual item is not currently implemented");
		return Mono.just("OK");
	}

  @Override
  public Mono<String> deleteHold(String id) {
		log.info("Delete hold is not currently implemented");
		return Mono.empty();
  }

  public Mono<String> deletePatron(String id) {
		log.info("Delete patron is not currently implemented");
		return Mono.from(client.deleteAlmaUser(id))
			.then(Mono.just("OK"));
	}


	@Override
	public Mono<String> deleteBib(String id) {
		log.info("Delete virtual bib is not currently implemented");
		return Mono.empty();
	}

	@Override
	public @NonNull String getClientId() {
			return "";
	}

  @Override
  public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
    return Mono.empty();
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

}

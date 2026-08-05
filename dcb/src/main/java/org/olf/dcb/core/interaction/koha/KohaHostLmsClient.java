package org.olf.dcb.core.interaction.koha;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.interaction.koha.dto.*;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.model.*;


import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.zalando.problem.Problem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static io.micrometer.common.util.StringUtils.isBlank;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.ReactorUtils.raiseError;

/** <p> This is the client for the Koha library management system, and a crucial part of the Koha adapter.
 * </p><br>
 * <p> A useful first point of call for developers interested in this area is the Koha API docs site: <a href="https://api.koha-community.org/">Koha API documentation</a> </p>
 * <p>
 * You can also find OpenRS-specific developer documentation here (TBC).
 * The methods in this class are divided into groups based on what they do, and then alphabetically ordered,
 * to make this code easier to scan.
 * Please respect the ordering if adding new methods, as it makes maintaining this a lot easier.
 * If you are unsure of where a new method belongs, put it under 'General'
 * */

@Slf4j
@Prototype
public class KohaHostLmsClient implements HostLmsClient {

	private final HostLms hostLms;
	private final KohaApiClient client;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final KohaClientConfig config;



	// Static defaults for virtual patron creation
	private static final String DEFAULT_FIRST_NAME = "DCB";
	private static final String DEFAULT_LAST_NAME = "VPATRON";

	public KohaHostLmsClient(
		@Parameter HostLms hostLms,
		ReferenceValueMappingService referenceValueMappingService,
		KohaClientFactory kohaClientFactory,
		MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService,
		LocationToAgencyMappingService locationToAgencyMappingService) {
		this.hostLms = hostLms;
		this.client = kohaClientFactory.createClientFor(hostLms);
		this.referenceValueMappingService = referenceValueMappingService;
		this.materialTypeToItemTypeMappingService = materialTypeToItemTypeMappingService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		// Built here rather than injected, as Alma does. KohaClientConfig has no bean
		// definition and its only constructor takes the HostLms, so it could never have
		// been satisfied as an injection point.
		this.config = new KohaClientConfig(hostLms);
		}

	/*** General operations - are we missing any? A version check would be useful. possibly implementer also***/

	@Override
	public HostLms getHostLms() {
		return hostLms;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		// To be implemented using a KohaClientConfig class
		return config.getSettings();
	}

	@Override
	public String getClientId() {
		// The Koha server, not the Host LMS code: several DCB Host LMS records can
		// point at one Koha, and returning the code would hide that from the
		// same-server checks entirely.
		return qualifySystemIdentity(config.getApiUrl().resolve("/").toString());
	}

	@Override
	public Mono<Boolean> supplierPreflight(String borrowingAgencyCode, String supplyingAgencyCode, String canonicalItemType, String canonicalPatronType) {
		return Mono.error(new NotImplementedException("This functionality has not yet been implemented for Koha"));
	}

	private String getDcbSharingLibraryCode() {
		final var sharingLibraryCode = config.getDcbSharingLibraryCode();
		if (isBlank(sharingLibraryCode)) throw new IllegalStateException("Missing DCB sharing library code in config. Please check the client configuration of this Koha LMS.");
		return sharingLibraryCode;
	}

	// This is about how we resolve the location to either a Koha pickup location (From the local ID) or an external one
	// For external locations either we need to create a reference or have a "DCB" library with details
	// We might learn lessons here we can use elsewhere
	private String resolveLibraryCode(PlaceHoldRequestParameters p) {
		if (p.getPickupLocation() != null && p.getPickupLocation().getLocalId() != null) {
			return p.getPickupLocation().getLocalId();
		}
		if (p.getPickupLocationCode() != null) {
			return p.getPickupLocationCode();
		}
		return getDcbSharingLibraryCode();
	}

	/*** Patron operations ***/

	// We're going to need a way of getting the library
	// Think AspenCat
	// Can we use agencies for this
	// 1 system
	@Override
	public Mono<String> createPatron(Patron patron) {
		// For creating a virtual patron
		log.info("Attempting to create a patron for Koha with Patron: {}", patron);

		// Extract names
		String firstName = (patron.getLocalNames() != null && !patron.getLocalNames().isEmpty())
			? patron.getLocalNames().get(0) : DEFAULT_FIRST_NAME;
		String lastName = (patron.getLocalNames() != null && patron.getLocalNames().size() > 1)
			? patron.getLocalNames().get(patron.getLocalNames().size() - 1) : DEFAULT_LAST_NAME;

		// Koha POST /api/v1/patrons strictly requires surname, library_id, and category_id
		// We will need to define a default library for virtual patrons
		// then we have the joy of the shared libraries ....
		// and we MUST be able to distinguish DCB patrons from real ones
		String libraryId = getConfig().getOrDefault("default-agency-code", "").toString();
		String categoryId = patron.getLocalPatronType() != null ? patron.getLocalPatronType() : "DCB";

		KohaPatron kohaPatron = new KohaPatron(
			null, // patron_id generated by Koha
			patron.getUniqueIds() != null && !patron.getUniqueIds().isEmpty() ? patron.getUniqueIds().get(0) : null, // cardnumber
			lastName, // surname (Required)
			firstName, // firstname
			null, // email
			libraryId, // library_id (Required)
			categoryId, // category_id (Required)
			null, null, null, null, null, null, null, null, null, null
		);

		return client.createPatron(kohaPatron)
			.map(returnedPatron -> String.valueOf(returnedPatron.getPatronId()))
			.doOnSuccess(id -> log.info("Created Koha patron with ID: {}", id));
	}

	@Override
	public Mono<String> deletePatron(String id) {
		log.info("Deleting Koha patron: {}", id);
		return client.deletePatron(id)
			.thenReturn("OK")
			.doOnError(e -> log.error("Failed to delete Koha patron {}: {}", id, e.getMessage()));
	}

	// Get the canonical patron type
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
				"Unable to map patron type \"" + localPatronType + "\" for Koha Host LMS: \"" + hostLmsCode + "\" to canonical value",
				hostLmsCode, localPatronType)));
	}

	@Override
	public Mono<String> findLocalPatronType(String canonicalPatronType) {
		if (canonicalPatronType == null) {
			return Mono.empty();
		}

		return referenceValueMappingService.findMapping("patronType", "DCB", canonicalPatronType, "patronType", getHostLmsCode())
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.error(new NoPatronTypeMappingFoundException(
				"Unable to map canonical patron type \"" + canonicalPatronType + "\" to a patron type on Koha Host LMS: \"" + getHostLmsCode() + "\"",
				getHostLmsCode(), canonicalPatronType)));
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		log.info("Finding virtual patron {}", patron);

		final var uniqueId = getValueOrNull(patron, org.olf.dcb.core.model.Patron::determineUniqueId);
		if (uniqueId == null) {
			return Mono.error(new IllegalArgumentException("Unable to find uniqueId for virtual patron"));
		}

		return client.getPatronByCardnumber(uniqueId)
			.flatMap(list -> {
				if (list != null && !list.isEmpty()) {
					log.info("Found virtual patron with cardnumber: {}", uniqueId);
					return Mono.just(mapKohaPatronToDcbPatron(list.get(0)));
				}
				return Mono.empty();
			})
			.switchIfEmpty(Mono.error(VirtualPatronNotFound.builder()
				.withDetail("No Koha patron found")
				.with("uniqueId", uniqueId)
				.build()));
	}

	@Override
	public Mono<Patron> getPatronByIdentifier(String id) {
		log.debug("Fetching patron by identifier (cardnumber): {}", id);
		return client.getPatronByCardnumber(id)
			.flatMap(list -> {
				if (list != null && !list.isEmpty()) {
					return Mono.just(mapKohaPatronToDcbPatron(list.get(0)));
				}
				return Mono.empty();
			})
			.doOnError(e -> log.error("Failed to fetch patron by identifier {}: {}", id, e.getMessage()));
	}

	@Override
	public Mono<Patron> getPatronByUsername(String username) {
		log.debug("Fetching patron by username (userid): {}", username);
		return client.getPatronByUserId(username)
			.flatMap(list -> {
				if (list != null && !list.isEmpty()) {
					return Mono.just(mapKohaPatronToDcbPatron(list.get(0)));
				}
				return Mono.empty();
			})
			.doOnError(e -> log.error("Failed to fetch patron by username {}: {}", username, e.getMessage()));
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		return client.getPatron(localPatronId).map(this::mapKohaPatronToDcbPatron);
	}


	@Override
	public Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret) {
		log.debug("Authenticating patron with principle: {}", patronPrinciple);

		return client.authenticatePatron(patronPrinciple, secret)
			.flatMap(response -> {
				if (response.getPatronId() != null ) {
					String patronId = String.valueOf(response.getPatronId());
					return client.getPatron(patronId).map(this::mapKohaPatronToDcbPatron);
				}
				return Mono.empty();
			})
			.doOnError(e -> log.error("Failed to authenticate Koha patron {}: {}", patronPrinciple, e.getMessage()));
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		log.debug("Updating Koha patron - localID {} and patron type {}", localId, patronType);

		return client.getPatron(localId)
			.flatMap(existingPatron -> {
				existingPatron.setCategoryId(patronType);
				return client.updatePatron(localId, existingPatron);
			})
			.map(this::mapKohaPatronToDcbPatron)
			.doOnError(e -> log.error("Failed to update Koha patron {}: {}", localId, e.getMessage()));
	}

	private Patron mapKohaPatronToDcbPatron(KohaPatron kohaPatron) {
		return Patron.builder()
			.localId(List.of(String.valueOf(kohaPatron.getPatronId())))
			.localNames(List.of(kohaPatron.getFirstname(), kohaPatron.getSurname()))
			.localBarcodes(kohaPatron.getCardnumber() != null ? List.of(kohaPatron.getCardnumber()) : List.of())
			.localPatronType(kohaPatron.getCategoryId())
			.isActive(true)
			.build();
	}

	/*** Bib and item operations ***/

	@Override
	public Mono<String> createBib(Bib bib) {
		log.debug("Koha: Creating virtual bib record for title: {}", bib.getTitle());

		final var author = (bib.getAuthor() != null) ? bib.getAuthor() : "";
		final var title = (bib.getTitle() != null) ? bib.getTitle() : "DCB Virtual Record";

		final String kohaBibXml = KohaMarcXmlGenerator.createBibXml(title, author);

		return client.createBibRecord(kohaBibXml)
			.map(kohaBiblio -> String.valueOf(kohaBiblio.getBiblioId()))
			.doOnSuccess(id -> log.info("Successfully created Koha virtual bib with ID: {}", id))
			.doOnError(e -> log.error("Failed to create Koha virtual bib: {}", e.getMessage()));
	}

	@Override
	public Mono<HostLmsItem> getItem(HostLmsItem hostLmsItem) {
		final String itemId = getValueOrNull(hostLmsItem, HostLmsItem::getLocalId);

		if (itemId == null || itemId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Local item ID is required to fetch a Koha item"));
		}

		log.debug("Fetching Koha item by local ID: {}", itemId);

		return client.getItem(itemId)
			.map(this::mapKohaItemToHostLmsItem) // This might not give us all the context, so see how this does
			.doOnError(e -> log.error("Failed to retrieve Koha item {}: {}", itemId, e.getMessage()));
	}

	/**
	 * Helper method to fetch a Koha item by barcode and map it to a DCB HostLmsItem.
	 * To be replicated across all LMS to provide "lookup by barcode". Not used just yet.
	 */
	public Mono<HostLmsItem> getItemByBarcode(String barcode) {
		log.info("Fetching Koha item by barcode: {}", barcode);

		return client.getItemByBarcode(barcode)
			.flatMap(items -> {
				if (items == null || items.isEmpty()) {
					log.warn("No item found in Koha for barcode: {}", barcode);
					return Mono.empty();
				}

				// Since barcodes should be unique, we safely take the first match
				KohaItem kohaItem = items.get(0);
				return Mono.just(mapKohaItemToHostLmsItem(kohaItem));
			})
			.doOnError(e -> log.error("Failed to fetch item by barcode {} from Koha: {}", barcode, e.getMessage()));
	}

	/**
	 * Maps the KohaItem DTO to DCB's HostLmsItem
	 */
	private HostLmsItem mapKohaItemToHostLmsItem(KohaItem kohaItem) {
		return HostLmsItem.builder()
			.localId(String.valueOf(kohaItem.getItemId()))
			.barcode(kohaItem.getExternalId()) // are barcodes the ONLY thing here?
			.bibId(String.valueOf(kohaItem.getBiblioId()))
			.status(deriveItemStatusForHostLmsItem(kohaItem))
			.renewalCount(kohaItem.getRenewalsCount())
			.holdCount(kohaItem.getHoldCount()) // Commented out ones need a bit more thought ...
//			.holdingId(kohaItem.getHoldingLibraryId())
//			.renewable()
//			.rawStatus(kohaItem.get)
			.build();
	}

	/**
	 * Helper to map Koha's numeric statuses to DCB's canonical statuses.
	 * Could be different between implementations so watch out
	 * Also need one for LA. Expand this
	 *
	 */
	private String deriveItemStatusForHostLmsItem(KohaItem item) {
		// This is for the Host LMS item. We'll need another one to make sense of everything else
		// or possibly a combo

		if (item.getWithdrawn() != null && item.getWithdrawn() > 0) {
			return HostLmsItem.ITEM_MISSING;
		}
		if (item.getLostStatus() != null && item.getLostStatus() > 0) {
			return HostLmsItem.ITEM_MISSING;
		}
		if (item.getNotForLoanStatus() != null && item.getNotForLoanStatus() > 0) {
			return HostLmsItem.ITEM_MISSING; // Really should be ITEM_UNAVAILABLE or ITEM_RESTRICTED
		}
//		if (item.getDamagedStatus() != null && item.getDamagedStatus() > 0) {
//		}
		// Need to look at Koha item statuses for this one
		// Should really default to UNKNOWN
		return HostLmsItem.ITEM_AVAILABLE;
	}

	private ItemStatusCode deriveItemStatus(KohaItem item) {
		// Think about these
		// Do Koha libraries have a restricted status they would use for suppression? Or do they use not for loan
		// We could standardise on a restricted status of X. Note a non zero value blocks checkouts/holds
		// Maybe a not for loan would work https://bywatersolutions.com/education/an-overview-of-item-statuses
		// i.e. not for loan of 42 = suppressed from DCB?
		// Or we use a collection code of NOT_FOR_DCB or something similar. Talk to libraries
		// Other options: notes, restricted statuses

		if (item.getCheckout() != null) {
			return ItemStatusCode.CHECKED_OUT;
		}
		if (item.getWithdrawn() != null && item.getWithdrawn() > 0) {
			return ItemStatusCode.UNAVAILABLE;
		}
		if (item.getLostStatus() != null && item.getLostStatus() > 0) {
			return ItemStatusCode.UNAVAILABLE;
		}
		if (item.getDamagedStatus() != null && item.getDamagedStatus() > 0) {
			return ItemStatusCode.UNAVAILABLE;
		}
		if (item.getNotForLoanStatus() != null && item.getNotForLoanStatus() > 0) {
			return ItemStatusCode.UNAVAILABLE;
		}
		if (item.getRestrictedStatus() != null && item.getRestrictedStatus() > 0) {
			return ItemStatusCode.UNAVAILABLE;
		}

		return ItemStatusCode.AVAILABLE;
	}

	/**
	 * Fetches all items for a given bibliographic record.
	 * <p>
	 * NOTE: Pagination depends on local Koha system preferences. The GET /api/v1/biblios/{biblio_id}/items
	 * endpoint respects the `_per_page` setting. If a biblio has a massive number of items, this array
	 * may be truncated unless you loop pages or set a sufficiently high `_per_page` value.
	 * </p>
	 */
	@Override
	public Mono<List<Item>> getItems(BibRecord bibRecord) {
		final String bibId = bibRecord.getSourceRecordId();
		log.debug("Fetching items for Koha bib: {}", bibId);

		if (bibId == null || bibId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Bib ID is required to fetch Koha items"));
		}

		return client.getItemsForBiblio(bibId)
			.flatMapMany(Flux::fromArray)
			.flatMap(this::mapKohaItemToDcbItem)
			.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, getHostLmsCode()))
			.flatMap(materialTypeToItemTypeMappingService::enrichItemWithMappedItemType)
			.onErrorContinue((throwable, item) -> log.warn("Mapping error for Koha item {}: {}", item, throwable.getMessage()))
			.collectList();
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		final String bibId = getValueOrNull(cic, CreateItemCommand::getBibId);
		final String barcode = getValueOrNull(cic, CreateItemCommand::getBarcode);

		if (bibId == null || barcode == null) {
			return Mono.error(new IllegalArgumentException("Bib ID and Barcode are required to create a Koha item"));
		}

		log.info("Koha: Creating virtual item for bibId: {}, barcode: {}", bibId, barcode);

		final String targetLibraryCode = config.getVirtualItemLibraryCode();
//		final Integer virtualNotForLoanStatus = -1; // We might need to make this configurable

		return getMappedItemType(cic.getCanonicalItemType())
			.switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown canonical item type: " + cic.getCanonicalItemType())))
			.flatMap(itemType -> {

				KohaItem virtualItem = KohaItem.builder()
					.externalId(barcode) // Maps to Koha's barcode field
					.itemTypeId(itemType)
					.homeLibraryId(targetLibraryCode)
					.holdingLibraryId(targetLibraryCode)
					// Can we use an item type or a status to make it clear these aren't requestable
					// Koha admins might need to do this for us and then we can use a "DCB_VIRTUAL" item type to hide them from OPAC
//					.notForLoanStatus(virtualNotForLoanStatus) // This could also make it so that virtual items are NOT requestable by local patrons.
					// But we need to check it won't break checkouts (or that we can override) Override can be done via confirmation token so one to experiment with
					.callnumber("DCB_VIRTUAL_COLLECTION")
					.internalNotes("Virtual item = created by DCB")
					.publicNotes("Virtual item = created by DCB")
					.build();

				// POST /api/v1/biblios/{biblio_id}/items
				return client.createItem(bibId, virtualItem);
			})
			.map(this::mapKohaItemToHostLmsItem)
			.doOnSuccess(item -> log.info("Successfully created Koha virtual item: {}", item.getLocalId()))
			.doOnError(e -> log.error("Failed to create Koha virtual item for bib {}: {}", bibId, e.getMessage()));
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

	private Mono<Item> mapKohaItemToDcbItem(KohaItem kohaItem) {
		final String itemId = String.valueOf(kohaItem.getItemId());
		final String bibId = String.valueOf(kohaItem.getBiblioId());

		// First we need the active holds.
		return client.getActiveHoldsForItem(itemId)
			.map(holds -> holds != null ? holds.length : 0)
			.onErrorResume(e -> {
				log.warn("Failed to fetch active holds for Koha item {}: {}", itemId, e.getMessage());
				return Mono.just(0);
			})
			.map(activeHoldCount -> {
				ItemStatusCode dcbStatusCode = deriveItemStatus(kohaItem);
				Boolean isRequestable = ItemStatusCode.AVAILABLE.equals(dcbStatusCode); // CHECK THAT THIS IS THE SAME THING IN KOHA


				Instant dueDate = null;
				if (kohaItem.getCheckout() != null && kohaItem.getCheckout().getDueDate() != null) { // This needs careful parsing
					dueDate = Instant.parse(kohaItem.getCheckout().getDueDate());
				}

				// Deriving the location. Can we do better here - look at the others, maybe we can do something new
				Location derivedLocation = kohaItem.getLocation() != null
					? Location.builder()
					.code(kohaItem.getLocation())
					.name(kohaItem.getLocation())
					.build()
					: null;

				// Suppression needs work. assume a "42" in the not for loan means local only for now.
				boolean isSuppressedFromDCB = kohaItem.getNotForLoanStatus() == 42;
				Boolean isSuppressed = kohaItem.getWithdrawn() != null && kohaItem.getWithdrawn() > 0 || isSuppressedFromDCB;

				return Item.builder()
					.localId(itemId)
					.status(new ItemStatus(dcbStatusCode))
					.dueDate(dueDate)
					.location(derivedLocation)
					.barcode(kohaItem.getExternalId())
					.callNumber(kohaItem.getCallnumber())
					.isRequestable(isRequestable)
					.holdCount(activeHoldCount) // Uses active holds, not the historical hold_count tally
					.localBibId(bibId)
					.localItemType(kohaItem.getEffectiveItemTypeId() != null ? kohaItem.getEffectiveItemTypeId() : kohaItem.getItemTypeId())
					.localItemTypeCode(kohaItem.getEffectiveItemTypeId() != null ? kohaItem.getEffectiveItemTypeId() : kohaItem.getItemTypeId())
					.suppressed(isSuppressed)
					.owningContext(getHostLms().getCode())
					.build();
			});
	}


	@Override
	public Mono<String> deleteItem(DeleteCommand deleteCommand) {
		final String itemId = getValueOrNull(deleteCommand, DeleteCommand::getItemId);

		if (itemId == null || itemId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Item ID is required to delete a Koha item"));
		}

		log.info("Koha: Deleting item: {}", itemId);
		return client.deleteItem(itemId)
			.thenReturn("OK")
			.doOnError(e -> log.error("Failed to delete Koha item {}: {}", itemId, e.getMessage()));
	}

	@Override
	public Mono<String> deleteBib(String id) {
		if (id == null || id.isBlank()) {
			return Mono.error(new IllegalArgumentException("Bib ID is required to delete a Koha bib"));
		}

		log.info("Koha: Deleting bib record: {}", id);
		return client.deleteBib(id)
			.thenReturn("OK")
			.doOnError(e -> log.error("Failed to delete Koha bib {}: {}", id, e.getMessage()));
	}

	// Possible trap here with Koha item statuses being a little strange

	@Override
	public Mono<String> updateItemStatus(HostLmsItem hostLmsItem, CanonicalItemState crs) {
		return Mono.error(new NotImplementedException("This functionality has not yet been implemented for Koha"));
	}

	/*** Hold request and checkout operations ***/

	@Override
	public Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters) {
		final String localRequestId = parameters.getLocalRequestId();

		// Can we add a cancel reason
		if (localRequestId == null || localRequestId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Local request ID is required to cancel a Koha hold"));
		}

		log.info("Koha: Cancelling hold request: {}", localRequestId);
		return client.deleteHold(localRequestId)
			.thenReturn("OK")
			.doOnError(e -> log.error("Failed to cancel Koha hold {}: {}", localRequestId, e.getMessage()));
	}

	@Override
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkoutItemCommand) {
		final String patronId = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getPatronId);
		final String itemBarcode = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getItemBarcode);

		if (itemBarcode.isBlank()|| patronId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Item barcode and Patron ID are required for Koha checkout"));
		}

		log.info("Koha: Checking out item (barcode: {}) to patron {}", itemBarcode, patronId);

		KohaCheckoutRequest req = new KohaCheckoutRequest(
			Long.valueOf(patronId),
			null,
			itemBarcode
		);

		return client.checkoutItem(req)
			.thenReturn("OK")
			.doOnError(e -> log.error("Koha checkout failed for patron {} and item {}: {}", patronId, itemBarcode, e.getMessage()));
	}

	@Override
	public Mono<String> checkInItem(CheckInItemCommand checkInItemCommand) {
		return Mono.error(new NotImplementedException("This functionality has not yet been implemented for Koha"));
	}

	@Override
	public Mono<String> deleteHold(DeleteCommand deleteCommand) {
		final String holdId = getValueOrNull(deleteCommand, DeleteCommand::getRequestId);

		if (holdId == null || holdId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Hold ID is required to delete a Koha hold"));
		}

		log.info("Koha: Deleting hold: {}", holdId);
		return client.deleteHold(holdId)
			.thenReturn("OK")
			.doOnError(e -> log.error("Failed to delete Koha hold {}: {}", holdId, e.getMessage()));
	}
	@Override
	public Mono<HostLmsRequest> getRequest(HostLmsRequest request) {
		// Note: we may need to take into account the need to find holds for a patron, either here or elsewhere
		final String localRequestId = getValueOrNull(request, HostLmsRequest::getLocalId);
//		final var patronId = getValueOrNull(request, HostLmsRequest::getLocalPatronId);

		if (localRequestId == null || localRequestId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Local request ID is required to fetch a Koha hold"));
		}

		log.debug("Fetching Koha hold request by ID: {}", localRequestId);

		return client.getHold(localRequestId)
			.map(kohaHold -> HostLmsRequest.builder()
				.localId(String.valueOf(kohaHold.getHoldId()))
				.status(checkHoldStatus(kohaHold.getStatus()))
				.rawStatus(kohaHold.getStatus())
				.requestedItemId(kohaHold.getItemId() != null ? String.valueOf(kohaHold.getItemId()) : null)
				.build())
			.doOnError(e -> log.error("Failed to retrieve Koha hold request {}: {}", localRequestId, e.getMessage()));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters) {
		// For an item level hold, we must provide patron ID, bib ID, item ID,  and pickup library ID (for borrowing, this corresponds to the pickup location in Koha)
		// We can provide notes, a priority flag, a hold date, and an item group id
		// Construct Koha hold request
		// Deal with it if the values aren't integers
		log.info("placeHoldRequestAtBorrowingAgency patron={} item={}", parameters.getLocalPatronId(), parameters.getLocalItemId());
		return Mono.just(parameters)
			.map(p -> WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW.equals(p.getActiveWorkflow())
				? getDcbSharingLibraryCode() : resolveLibraryCode(p))
			.flatMap(lib -> submitKohaHold(parameters, lib));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters parameters) {
		log.info("placeHoldRequestAtSupplyingAgency patron={} item={}", parameters.getLocalPatronId(), parameters.getLocalItemId());
		// If we are dealing with the expedited checkout workflow, the pickup is NOT external.
		// Otherwise, the pickup is external
		return Mono.just(parameters)
			.map(p -> WorkflowConstants.EXPEDITED_WORKFLOW.equals(p.getActiveWorkflow())
				? resolveLibraryCode(p) : getDcbSharingLibraryCode())
			.flatMap(lib -> submitKohaHold(parameters, lib));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters parameters) {
		log.info("placeHoldRequestAtPickupAgency patron={} item={}", parameters.getLocalPatronId(), parameters.getLocalItemId());
		return Mono.just(parameters)
			.map(this::resolveLibraryCode)
			.flatMap(lib -> submitKohaHold(parameters, lib));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters parameters) {
		log.info("placeHoldRequestAtLocalAgency patron={} item={}", parameters.getLocalPatronId(), parameters.getLocalItemId());
		return Mono.just(parameters)
			.map(this::resolveLibraryCode)
			.flatMap(lib -> submitKohaHold(parameters, lib));
	}

	private Mono<LocalRequest> submitKohaHold(PlaceHoldRequestParameters parameters, String pickupLibraryId) {
		// pickup library ID needs to reference either Koha location or external ones
		return Mono.defer(() -> {
			KohaHoldRequest holdRequest = KohaHoldRequest.builder()
				.itemId(Integer.valueOf(parameters.getLocalItemId()))
				.biblioId(Integer.valueOf(parameters.getLocalBibId()))
				.patronId(Integer.valueOf(parameters.getLocalPatronId()))
				.pickupLibraryId(pickupLibraryId)
				.notes(parameters.getNote())
//				.expirationDate()
//				.nonPriority()
//				.itemTypeId()
//				.holdDate()
				.build();

			return client.placeHoldRequest(holdRequest)
				.map(this::mapKohaHoldToLocalRequest)
				.doOnSubscribe(s -> log.info("Submitting Koha HOLD patron={} item={} pickupLibrary={}",
					parameters.getLocalPatronId(), parameters.getLocalItemId(), pickupLibraryId));
		});
	}

	private LocalRequest mapKohaHoldToLocalRequest(KohaHoldResponse response) {
		String mappedStatus = checkHoldStatus(response.getStatus());

		return LocalRequest.builder()
			.localId(String.valueOf(response.getHoldId()))
			.localStatus(mappedStatus)
			.rawLocalStatus(response.getStatus())
			.requestedItemId(response.getItemId() != null ? String.valueOf(response.getItemId()) : null)
			.requestedItemBarcode(response.getItem().getExternalId())
			.build();
	}

	private String checkHoldStatus(String kohaStatus) {

		// review this
		if (kohaStatus == null) return HostLmsRequest.HOLD_PLACED;

		return switch (kohaStatus.toUpperCase()) {
			case "REJECTED" -> HostLmsRequest.HOLD_CANCELLED;
			case "AVAILABLE", "W" -> HostLmsRequest.HOLD_READY;
			case "T" -> HostLmsRequest.HOLD_TRANSIT;
			default -> HostLmsRequest.HOLD_PLACED;
		};
	}

	@Override
	public Mono<LocalRequest> updateHoldRequest(LocalRequest localRequest) {
		return Mono.error(new NotImplementedException("This functionality has not yet been implemented for Koha"));
	}

	/*** Renewal operations ***/

	@Override
	public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
		return Mono.error(new NotImplementedException("This functionality has not yet been implemented for Koha"));
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal hostLmsRenewal) {
		final String checkoutId = hostLmsRenewal.getLocalRequestId();

		if (checkoutId == null || checkoutId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Checkout ID is required to renew a Koha loan"));
		}

		log.info("Koha: Renewing checkout: {}", checkoutId);

		return client.renewCheckout(checkoutId)
			.map(kohaCheckout -> {
				log.info("Due date {}", kohaCheckout.getDueDate());
				return hostLmsRenewal;
			})
			.doOnError(e -> log.error("Failed to renew Koha checkout {}: {}", checkoutId, e.getMessage()));
	}
}

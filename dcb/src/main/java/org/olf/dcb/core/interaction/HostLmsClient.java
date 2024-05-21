package org.olf.dcb.core.interaction;

import java.util.List;
import java.util.Map;

import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;

import reactor.core.publisher.Mono;

public interface HostLmsClient {
	// All implementations must understand these states and be able to translate
	// them to
	// local values when encountered via updateRequestStatus
	enum CanonicalRequestState {
		PLACED, TRANSIT;
	}

	enum CanonicalItemState {
		AVAILABLE, TRANSIT, OFFSITE, RECEIVED, MISSING, ONHOLDSHELF, COMPLETED;
	}

	HostLms getHostLms();

	default String getHostLmsCode() {
		return getHostLms().getCode();
	}

	default Map<String, Object> getConfig() {
		return getHostLms().getClientConfig();
	}

	List<HostLmsPropertyDefinition> getSettings();

	Mono<List<Item>> getItems(BibRecord bibRecord);

	Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron);

	Mono<String> createPatron(Patron patron);

	Mono<String> createBib(Bib bib);

	/**
	 * place a hold for a specified item at a supplying agency.
	 * NOTE: Different systems have different policies. We may need to place a Bib or an Item level hold. If we place a bib level
	 * hold we will not know the barcode and id of the item held until the downstream system has selected an item.
	 * implementers need to take care to return the id and barcode of the ultimately selected item once it is known.
	 */
	Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters parameters);

	Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters);

	Mono<String> findLocalPatronType(String canonicalPatronType);

	Mono<String> findCanonicalPatronType(String localPatronType, String localId);

	// Look up patron by their internal id - e.g. 1234
	Mono<Patron> getPatronByLocalId(String localPatronId);

	// Look up patron by the string they use to identify themselves on the login screen - e.g. fred.user
	Mono<Patron> getPatronByUsername(String username);

	Mono<Patron> updatePatron(String localId, String patronType);

	Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret);

	Mono<HostLmsItem> createItem(CreateItemCommand createItemCommand);

	Mono<HostLmsRequest> getRequest(String localRequestId);

	Mono<HostLmsItem> getItem(String localItemId, String localRequestId);

	Mono<String> updateItemStatus(String itemId, CanonicalItemState crs, String localRequestId);

	// WARNING We might need to make this accept a patronIdentity - as different
	// systems might take different ways to identify the patron
	Mono<String> checkOutItemToPatron(String itemId, String itemBarcode, String patronId, String patronBarcode, String localRequestId);

	Mono<String> deleteItem(String id);

	Mono<String> deleteBib(String id);

	Mono<String> deleteHold(String id);

	/**
	 * Return a boolean value which tests if an agency is able to act as a supplier for an item identified
	 * via RTAC. Should test if the canonical item and patron types needed to fulfil the role can be mapped.
	 * Note that a primary concern is also if the item and patron types can be mapped back to the borrowing
	 * system. For example, when requesting Nonagon Infinity from STLouis the local item type is 9. We must be
	 * able to sensibly map this into a canonical item type and from the canonical item type into a type for
	 * the patron home agency.
	 */
	Mono<Boolean> supplierPreflight(String borrowingAgencyCode, String supplyingAgencyCode, String canonicalItemType, String canonicalPatronType);

	default boolean reflectPatronLoanAtSupplier() {
		return true;
	}
}

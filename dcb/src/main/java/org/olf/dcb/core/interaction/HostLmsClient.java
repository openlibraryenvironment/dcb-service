package org.olf.dcb.core.interaction;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

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
		AVAILABLE, TRANSIT, OFFSITE, RECEIVED, MISSING, ONHOLDSHELF;

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

	Mono<String> createPatron(Patron patron);

	default Mono<Patron> findVirtualPatron(String localBarcode, org.olf.dcb.core.model.Patron patron) {
		final var uniqueId = getValue(patron, org.olf.dcb.core.model.Patron::getUniqueId);

		return patronAuth("UNIQUE-ID", uniqueId, localBarcode);
	}

	Mono<String> createBib(Bib bib);

	Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters parameters);

	Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters);

	// Look up patron by their internal id - e.g. 1234
	Mono<Patron> getPatronByLocalId(String localPatronId);

	// Look up patron by the string they use to identift themselves on the login screen - e.g. fred.user
	Mono<Patron> getPatronByUsername(String username);

	Mono<Patron> updatePatron(String localId, String patronType);

	Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret);

	Mono<HostLmsItem> createItem(CreateItemCommand createItemCommand);

	Mono<HostLmsHold> getHold(String holdId);

	Mono<HostLmsItem> getItem(String itemId);

	Mono<String> updateItemStatus(String itemId, CanonicalItemState crs);

	// WARNING We might need to make this accept a patronIdentity - as different
	// systems might take different ways to identify the patron
	Mono<String> checkOutItemToPatron(String itemId, String patronBarcode);

	Mono<String> deleteItem(String id);

	Mono<String> deleteBib(String id);
}

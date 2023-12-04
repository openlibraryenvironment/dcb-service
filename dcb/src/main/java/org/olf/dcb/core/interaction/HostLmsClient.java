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

	Mono<String> createBib(Bib bib);

	Mono<LocalRequest> placeHoldRequest(PlaceHoldRequestParameters parameters);

	Mono<Patron> getPatronByLocalId(String localPatronId);

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

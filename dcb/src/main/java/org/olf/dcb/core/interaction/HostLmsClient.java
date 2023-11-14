package org.olf.dcb.core.interaction;

import java.util.List;

import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

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

	List<HostLmsPropertyDefinition> getSettings();

	Mono<List<Item>> getItems(String localBibId);

	Mono<String> createPatron(Patron patron);

	Mono<String> createBib(Bib bib);

	// (localHoldId, localHoldStatus)

	Mono<Tuple2<String, String>> placeHoldRequest(String id, String recordType, String recordNumber,
			String pickupLocation, String note, String patronRequestId);

	boolean useTitleLevelRequest();

	boolean useItemLevelRequest();

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

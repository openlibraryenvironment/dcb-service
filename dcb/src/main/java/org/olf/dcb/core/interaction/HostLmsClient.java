package org.olf.dcb.core.interaction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;
import java.util.Map;

import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;

public interface HostLmsClient {

	// All implementations must understand these states and be able to translate them to
	// local values when encountered via updateRequestStatus
	enum CanonicalRequestState {
		PLACED,
		TRANSIT
	}

	enum CanonicalItemState {
                AVAILABLE,
                TRANSIT,
                OFFSITE
        }


	HostLms getHostLms();

	Flux<Map<String, ?>> getAllBibData();


	Mono<List<Item>> getItemsByBibId(String bibId, String hostLmsCode);

	Mono<String> createPatron(Patron patron);

	Mono<String> createBib(Bib bib);

	// (localId, localPtype)
	Mono<Tuple2<String, String>> patronFind(String uniqueId);

	// (localHoldId, localHoldStatus)
	Mono<Tuple2<String, String>> placeHoldRequest(
		String id, 
		String recordType,
		String recordNumber, 
		String pickupLocation,
		String note,
                String patronRequestId);

	// Flux<?> getAllAgencies();
	Mono<Patron> getPatronByLocalId(String localPatronId);

	Mono<Patron> updatePatron(String localId, String patronType);

	// Mono<HostLmsItem> createItem(String bibId, String locationCode, String barcode);
	Mono<HostLmsItem> createItem(CreateItemCommand createItemCommand);

	Mono<HostLmsHold> getHold(String holdId);

	Mono<HostLmsItem> getItem(String itemId);

	Mono<String> updateItemStatus(String itemId, CanonicalItemState crs);

        // WARNING We might need to make this accept a patronIdentity - as different systems might take different ways to identify the patron
        Mono<String> checkOutItemToPatron(String itemId, String patronBarcode);
}

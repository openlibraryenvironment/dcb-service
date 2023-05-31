package org.olf.reshare.dcb.core.interaction;

import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.core.model.Item;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;
import java.util.Map;

public interface HostLmsClient {

	HostLms getHostLms();

	Flux<Map<String, ?>> getAllBibData();

	Mono<List<Item>> getItemsByBibId(String bibId, String hostLmsCode);

	Mono<String> createPatron(String uniqueId, String patronType);

	Mono<String> createBib(String author, String title);

	Mono<String> patronFind(String uniqueId);

	// (localHoldId, localHoldStatus)
	Mono<Tuple2<String, String>> placeHoldRequest(String id, String recordType,
		String recordNumber, String pickupLocation);

        // Flux<?> getAllAgencies();

	Mono<HostLmsPatronDTO> getPatronByLocalId(String localPatronId);

	Mono<HostLmsItem> createItem(String bibId, String itemType, String locationCode, String barcode);

	Mono<HostLmsHold> getHold(String holdId);
}

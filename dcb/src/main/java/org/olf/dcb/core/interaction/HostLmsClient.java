package org.olf.dcb.core.interaction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;
import java.util.Map;

import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;

public interface HostLmsClient {

	HostLms getHostLms();

	Flux<Map<String, ?>> getAllBibData();

	Mono<List<Item>> getItemsByBibId(String bibId, String hostLmsCode);

	Mono<String> createPatron(String uniqueId, String patronType);

	Mono<String> createBib(String author, String title);

	// (localId, localPtype)
	Mono<Tuple2<String, String>> patronFind(String uniqueId);

	// (localHoldId, localHoldStatus)
	Mono<Tuple2<String, String>> placeHoldRequest(
		String id, 
		String recordType,
		String recordNumber, 
		String pickupLocation,
		String note);

	// Flux<?> getAllAgencies();
	Mono<HostLmsPatronDTO> getPatronByLocalId(String localPatronId);

	Mono<HostLmsPatronDTO> updatePatron(String localId, String patronType);

	Mono<HostLmsItem> createItem(String bibId, String locationCode, String barcode);

	Mono<HostLmsHold> getHold(String holdId);
}

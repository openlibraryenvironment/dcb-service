package org.olf.reshare.dcb.request.resolution.fake;

import org.olf.reshare.dcb.item.availability.LiveAvailability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Singleton
public class FakeLiveAvailabilityService implements LiveAvailability {
	private static final Logger log = LoggerFactory.getLogger(FakeLiveAvailabilityService.class);

	@Override
	public Mono<List<org.olf.reshare.dcb.item.availability.Item>> getAvailableItems(String bibRecordId, String hostLmsCode) {
		log.debug("getAvailableItems({}, {})", bibRecordId, hostLmsCode);

		return Mono.just(hostLmsCode)
			// step skipped to get hostlms client
			.flatMapMany(fakeClient -> getItems(bibRecordId, hostLmsCode))
				.collectList();
	}

	private static Flux<org.olf.reshare.dcb.item.availability.Item> getItems(
		String bibRecordId, String hostLmsCode) {
		log.debug("getItems({}, {})", bibRecordId, hostLmsCode);

		return Flux.just(createFakeItem("FAKE_ID_0", hostLmsCode),
			createFakeItem("FAKE_ID_1", hostLmsCode), createFakeItem("FAKE_ID_2", hostLmsCode));
	}

	private static org.olf.reshare.dcb.item.availability.Item createFakeItem(String id, String hostLmsCode) {
		return new org.olf.reshare.dcb.item.availability.Item(id,
			new org.olf.reshare.dcb.item.availability.Status("FAKE_STATUS_CODE", "FAKE_STATUS_DISPLAY_TEXT", "FAKE_STATUS_DUE_DATE"),
			new org.olf.reshare.dcb.item.availability.Location("FAKE_LOCATION_CODE","FAKE_LOCATION_NAME"),
			"FAKE_BARCODE",
			"FAKE_CALL_NUMBER",
			hostLmsCode);
	}
}

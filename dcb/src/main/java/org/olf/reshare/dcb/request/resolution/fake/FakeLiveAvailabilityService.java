package org.olf.reshare.dcb.request.resolution.fake;

import java.util.List;

import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.item.availability.Item;
import org.olf.reshare.dcb.item.availability.LiveAvailability;
import org.olf.reshare.dcb.item.availability.Location;
import org.olf.reshare.dcb.item.availability.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class FakeLiveAvailabilityService implements LiveAvailability {
	private static final Logger log = LoggerFactory.getLogger(FakeLiveAvailabilityService.class);

	@Override
	public Mono<List<Item>> getAvailableItems(String bibRecordId, HostLms hostLms) {
		log.debug("getAvailableItems({}, {})", bibRecordId, hostLms);

		String hostLmsCode = hostLms.getCode();
		log.debug("getAvailableItems({}, {})", bibRecordId, hostLmsCode);

		return Mono.just(List.of(
			createFakeItem("FAKE_ID_0", hostLmsCode),
			createFakeItem("FAKE_ID_1", hostLmsCode),
			createFakeItem("FAKE_ID_2", hostLmsCode)));
	}

	private static Item createFakeItem(String id, String hostLmsCode) {
		return new Item(id,
			new Status("FAKE_STATUS_CODE", "FAKE_STATUS_DISPLAY_TEXT", "FAKE_STATUS_DUE_DATE"),
			new Location("FAKE_LOCATION_CODE","FAKE_LOCATION_NAME"),
			"FAKE_BARCODE", "FAKE_CALL_NUMBER", hostLmsCode);
	}
}

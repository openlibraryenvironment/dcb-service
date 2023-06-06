package org.olf.reshare.dcb.request.resolution.fake;

import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;

import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.core.model.ItemStatus;
import org.olf.reshare.dcb.core.model.Location;
import org.olf.reshare.dcb.item.availability.AvailabilityReport;
import org.olf.reshare.dcb.item.availability.LiveAvailability;
import org.olf.reshare.dcb.request.resolution.ClusteredBib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class FakeLiveAvailabilityService implements LiveAvailability {
	private static final Logger log = LoggerFactory.getLogger(FakeLiveAvailabilityService.class);

	@Override
	public Mono<AvailabilityReport> getAvailableItems(ClusteredBib clusteredBib) {
		log.debug("getAvailableItems({})", clusteredBib);

		String hostLmsCode = "hostLmsCode";

		return Mono.just(AvailabilityReport.ofItems(
			createFakeItem("FAKE_ID_0", hostLmsCode),
			createFakeItem("FAKE_ID_1", hostLmsCode),
			createFakeItem("FAKE_ID_2", hostLmsCode)));
	}

	private static Item createFakeItem(String id, String hostLmsCode) {
		return new Item(id, new ItemStatus(AVAILABLE),
			null, Location.builder()
				.name("FAKE_LOCATION_NAME")
				.code("FAKE_LOCATION_CODE")
				.build(),
			"FAKE_BARCODE", "FAKE_CALL_NUMBER",
			hostLmsCode, true, 0);
	}
}

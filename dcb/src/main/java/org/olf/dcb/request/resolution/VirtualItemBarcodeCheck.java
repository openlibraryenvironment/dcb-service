package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Duration;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.storage.AgencyRepository;

import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Asks the systems a request would create a virtual item in whether an item with the supplier copy's
 * barcode is already there, which is the condition that makes creating the virtual item fail.
 */
@Slf4j
@Singleton
@AllArgsConstructor
public class VirtualItemBarcodeCheck {
	// A slower answer is no answer, and no answer leaves the copy selectable
	private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(5);

	private final HostLmsService hostLmsService;
	private final AgencyRepository agencyRepository;

	/**
	 * True only when a system reports an item with the copy's barcode. A failed or slow lookup, or a
	 * system that cannot look items up, is not proof and answers false.
	 */
	public Mono<Boolean> barcodeAlreadyPresent(Item item, ItemFilterParameters parameters) {
		final var barcode = getValueOrNull(item, Item::getBarcode);
		final var itemHostLmsCode = getValueOrNull(item, Item::getHostLmsCode);
		final var borrowingHostLmsCode = getValueOrNull(parameters, ItemFilterParameters::borrowingHostLmsCode);
		final var pickupAgencyCode = getValueOrNull(parameters, ItemFilterParameters::pickupAgencyCode);

		if (barcode == null || itemHostLmsCode == null || borrowingHostLmsCode == null) {
			return Mono.just(false);
		}

		return hostLmsService.getClientFor(itemHostLmsCode)
			.flatMap(supplierClient -> systemsToCheck(borrowingHostLmsCode, pickupAgencyCode)
				.filter(client -> createsAVirtualItemThere(client, supplierClient))
				.concatMap(client -> presentAt(client, barcode, item))
				.any(Boolean::booleanValue))
			.defaultIfEmpty(false)
			.onErrorResume(error -> {
				log.warn("Unable to look up supplier copy itemLms={} itemId={} for borrowingLms={} ({}), treating it as absent",
					itemHostLmsCode, item.getLocalId(), borrowingHostLmsCode, error.toString());

				return Mono.just(false);
			});
	}

	private Flux<HostLmsClient> systemsToCheck(String borrowingHostLmsCode, String pickupAgencyCode) {
		final var pickupClient = Mono.justOrEmpty(pickupAgencyCode)
			.flatMap(code -> Mono.from(agencyRepository.findOneByCode(code)))
			.mapNotNull(agency -> getValueOrNull(agency, DataAgency::getHostLms, DataHostLms::getId))
			.flatMap(hostLmsService::getClientFor);

		return Flux.concat(hostLmsService.getClientFor(borrowingHostLmsCode), pickupClient)
			.distinct(HostLmsClient::getHostLmsCode);
	}

	// The supplier's own server already holds the real copy, so no virtual item is created there
	private static boolean createsAVirtualItemThere(HostLmsClient client, HostLmsClient supplierClient) {
		return client.compareTo(supplierClient) != 0 && !client.canHoldTwoVirtualItemsForOneCopy();
	}

	private Mono<Boolean> presentAt(HostLmsClient client, String barcode, Item item) {
		return client.getItemByBarcode(barcode)
			.timeout(LOOKUP_TIMEOUT)
			.map(found -> {
				log.info("Supplier copy itemLms={} itemId={} not chosen: its barcode is already at {}",
					item.getHostLmsCode(), item.getLocalId(), client.getHostLmsCode());

				return true;
			})
			.defaultIfEmpty(false)
			.onErrorResume(error -> {
				log.warn("Could not look up supplier copy itemLms={} itemId={} at {} ({}), treating it as absent",
					item.getHostLmsCode(), item.getLocalId(), client.getHostLmsCode(), error.toString());

				return Mono.just(false);
			});
	}
}

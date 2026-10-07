package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.function.Function;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.storage.PatronRequestRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Excludes a supplier copy that an unfinished request already brings to a system this request
 * would also create a virtual item in, unless that system can carry a second virtual item for one copy.
 */
@Slf4j
@Singleton
@Order(ItemFilter.SAME_COPY_ORDER)
@AllArgsConstructor
public class ExcludeCopyPromisedToBorrowerItemFilter implements ItemFilter {
	private final PatronRequestRepository patronRequestRepository;
	private final HostLmsService hostLmsService;

	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		final var borrowingHostLmsCode = getValueOrNull(parameters,
			ItemFilterParameters::borrowingHostLmsCode);

		final var pickupAgencyCode = getValueOrNull(parameters,
			ItemFilterParameters::pickupAgencyCode);

		return item -> notPromised(item, borrowingHostLmsCode, pickupAgencyCode);
	}

	private Mono<Boolean> notPromised(Item item, String borrowingHostLmsCode, String pickupAgencyCode) {
		final var itemHostLmsCode = getValueOrNull(item, Item::getHostLmsCode);
		final var itemLocalId = getValueOrNull(item, Item::getLocalId);

		if (itemHostLmsCode == null || itemLocalId == null || borrowingHostLmsCode == null) {
			log.warn("Cannot evaluate notPromised, excluding item: itemLms={}, itemId={}, borrowingLms={}",
				itemHostLmsCode, itemLocalId, borrowingHostLmsCode);

			return Mono.just(false);
		}

		return Flux.from(patronRequestRepository.findSystemsHoldingVirtualItemForSupplierCopy(
				itemHostLmsCode, itemLocalId, borrowingHostLmsCode, pickupAgencyCode))
			.concatMap(this::canHoldSecondVirtualItem)
			.all(Boolean::booleanValue)
			.onErrorResume(error -> {
				log.warn("Unable to check whether itemLms={} itemId={} is promised to borrowingLms={} or pickupAgency={} ({}), excluding item",
					itemHostLmsCode, itemLocalId, borrowingHostLmsCode, pickupAgencyCode, error.toString());

				return Mono.just(false);
			});
	}

	private Mono<Boolean> canHoldSecondVirtualItem(String hostLmsCode) {
		return hostLmsService.getClientFor(hostLmsCode)
			.map(HostLmsClient::canHoldTwoVirtualItemsForOneCopy)
			.defaultIfEmpty(false);
	}
}

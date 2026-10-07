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
import reactor.core.publisher.Mono;

/**
 * Excludes a supplier copy that an unfinished request already brings to the same borrowing
 * system, unless that system can carry a second virtual item for one copy.
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

		return item -> notPromisedToBorrower(item, borrowingHostLmsCode);
	}

	private Mono<Boolean> notPromisedToBorrower(Item item, String borrowingHostLmsCode) {
		final var itemHostLmsCode = getValueOrNull(item, Item::getHostLmsCode);
		final var itemLocalId = getValueOrNull(item, Item::getLocalId);

		if (itemHostLmsCode == null || itemLocalId == null || borrowingHostLmsCode == null) {
			log.warn("Cannot evaluate notPromisedToBorrower, excluding item: itemLms={}, itemId={}, borrowingLms={}",
				itemHostLmsCode, itemLocalId, borrowingHostLmsCode);

			return Mono.just(false);
		}

		// A copy in the borrower's own system is lent through a real hold, with no virtual item
		if (itemHostLmsCode.equals(borrowingHostLmsCode)) {
			return Mono.just(true);
		}

		return Mono.from(patronRequestRepository.isSupplierCopyPromisedToBorrower(
				itemHostLmsCode, itemLocalId, borrowingHostLmsCode))
			.flatMap(promised -> promised
				? canHoldSecondVirtualItem(borrowingHostLmsCode)
				: Mono.just(true))
			.defaultIfEmpty(false)
			.onErrorResume(error -> {
				log.warn("Unable to check whether itemLms={} itemId={} is promised to borrowingLms={} ({}), excluding item",
					itemHostLmsCode, itemLocalId, borrowingHostLmsCode, error.toString());

				return Mono.just(false);
			});
	}

	private Mono<Boolean> canHoldSecondVirtualItem(String borrowingHostLmsCode) {
		return hostLmsService.getClientFor(borrowingHostLmsCode)
			.map(HostLmsClient::canHoldTwoVirtualItemsForOneCopy);
	}
}

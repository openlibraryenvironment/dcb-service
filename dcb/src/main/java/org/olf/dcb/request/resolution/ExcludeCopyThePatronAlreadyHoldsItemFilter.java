package org.olf.dcb.request.resolution;

import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.function.Function;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.storage.PatronRequestRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Excludes a supplier copy on which DCB's records show this patron already holds a live supplier
 * hold, from another request that is not finished. A second hold by the same patron on the same
 * copy is refused by the supplier. A copy is never excluded on an answer that is not proof.
 */
@Slf4j
@Singleton
@Order(ItemFilter.SAME_PATRON_ORDER)
@AllArgsConstructor
public class ExcludeCopyThePatronAlreadyHoldsItemFilter implements ItemFilter {
	private static final List<String> GONE_STATUSES = List.of(HOLD_CANCELLED, HOLD_MISSING);

	private final PatronRequestRepository patronRequestRepository;

	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		return item -> notHeldByThePatron(item, parameters);
	}

	private Mono<Boolean> notHeldByThePatron(Item item, ItemFilterParameters parameters) {
		final var itemHostLmsCode = getValueOrNull(item, Item::getHostLmsCode);
		final var itemLocalId = getValueOrNull(item, Item::getLocalId);
		final var patronId = getValueOrNull(parameters, ItemFilterParameters::patronId);
		final var patronLocalId = getValueOrNull(parameters, ItemFilterParameters::patronLocalId);
		final var patronHostLmsCode = getValueOrNull(parameters, ItemFilterParameters::borrowingHostLmsCode);

		if (itemHostLmsCode == null || itemLocalId == null
			|| (patronId == null && (patronLocalId == null || patronHostLmsCode == null))) {

			return Mono.just(true);
		}

		return Mono.from(patronRequestRepository.existsLiveSupplierHoldForPatronOnCopy(itemHostLmsCode,
				itemLocalId, patronId, patronLocalId, patronHostLmsCode,
				getValueOrNull(parameters, ItemFilterParameters::patronRequestId), GONE_STATUSES))
			.map(held -> !held)
			.defaultIfEmpty(true)
			.onErrorResume(error -> {
				log.warn("Unable to check whether the patron already holds itemLms={} itemId={} ({}), including item",
					itemHostLmsCode, itemLocalId, error.toString());

				return Mono.just(true);
			});
	}
}

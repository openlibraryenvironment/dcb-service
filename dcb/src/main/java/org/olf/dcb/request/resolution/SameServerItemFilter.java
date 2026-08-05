package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;

import java.util.function.Function;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Excludes items that live on the same physical system as the borrower but under
 * a different Host LMS record.
 * <p>
 * DCB fulfils those by creating a virtual bib and item in the borrower's system -
 * which, when both records address one server, means creating a duplicate of a
 * record that is already there. Two Host LMS records over one server is a
 * configuration for describing separately-administered libraries, not a licence to
 * lend an item to the database it already lives in.
 * <p>
 * Note what this does <em>not</em> exclude: several agencies under one Host LMS
 * record. That is the ordinary shared-system shape (one Koha, sixty libraries) and
 * it is handled by workflow routing sending it down RET-LOCAL, where a real hold is
 * placed on the real item and no virtual records are created at all.
 */
@Slf4j
@Singleton
@Order(ItemFilter.SAME_SERVER_ORDER)
@AllArgsConstructor
public class SameServerItemFilter implements ItemFilter {
	private final HostLmsService hostLmsService;

	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		final var borrowingHostLmsCode = getValueOrNull(parameters,
			ItemFilterParameters::borrowingHostLmsCode);

		return item -> fromSameServer(item, borrowingHostLmsCode);
	}

	private Mono<Boolean> fromSameServer(Item item, String borrowingHostLmsCode) {
		final var itemHostLmsCode = getValueOrNull(item, Item::getHostLmsCode);

		// A filter is a predicate. Raising here would abort resolution for the whole
		// cluster over one unattributable item, and which of those two happens would
		// depend on where this filter landed in the composite's ordering.
		if (itemHostLmsCode == null || borrowingHostLmsCode == null) {
			log.warn("Cannot evaluate fromSameServer, excluding item: itemLms={}, borrowingLms={}",
				itemHostLmsCode, borrowingHostLmsCode);

			return Mono.just(false);
		}

		// One Host LMS record hosting several agencies is the supported shared-system
		// shape, not the case this filter exists to catch.
		if (itemHostLmsCode.equals(borrowingHostLmsCode)) {
			return Mono.just(true);
		}

		// Ask the clients who they talk to. getClientId is the system-identity
		// primitive - it knows about adapters that reach several logical systems
		// through one URL, which reading a config key never could.
		return Mono.zip(hostLmsService.getClientFor(itemHostLmsCode),
				hostLmsService.getClientFor(borrowingHostLmsCode))
			.map(function((HostLmsClient itemClient, HostLmsClient borrowingClient) ->
				includeItem(itemHostLmsCode, borrowingHostLmsCode, itemClient, borrowingClient)))
			.onErrorResume(error -> {
				log.warn("Unable to compare systems for itemLms={} and borrowingLms={} ({}), excluding item",
					itemHostLmsCode, borrowingHostLmsCode, error.toString());

				return Mono.just(false);
			})
			.defaultIfEmpty(false);
	}

	private static boolean includeItem(String itemHostLmsCode, String borrowingHostLmsCode,
		HostLmsClient itemClient, HostLmsClient borrowingClient) {

		final var sameServer = itemClient.compareTo(borrowingClient) == 0;

		if (sameServer) {
			log.debug("Excluding item from same server: itemLms={}, borrowingLms={}, systemIdentity={}",
				itemHostLmsCode, borrowingHostLmsCode, itemClient.getClientId());
		}

		return !sameServer;
	}
}

package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.function.Function;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.VolumeDesignation;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Excludes a copy that is not the volume the request names. A copy with no volume statement
 * cannot be shown to be that volume, so it is excluded too; a request naming no volume
 * excludes nothing.
 */
@Singleton
@Order(ItemFilter.REQUESTED_VOLUME_ORDER)
public class RequestedVolumeItemFilter implements ItemFilter {
	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		final var requestedVolume = VolumeDesignation.normalise(
			getValueOrNull(parameters, ItemFilterParameters::requestedVolumeDesignation));

		if (requestedVolume == null) {
			return item -> Mono.just(true);
		}

		return item -> Mono.just(VolumeDesignation.matches(requestedVolume, VolumeDesignation.of(item)));
	}
}

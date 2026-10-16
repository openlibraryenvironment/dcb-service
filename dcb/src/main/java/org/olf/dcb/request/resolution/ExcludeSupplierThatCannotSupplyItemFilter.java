package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.function.Function;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.request.fulfilment.SupplierPreflightService;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Excludes a copy whose supplier cannot supply because of a fault in its configuration, so the
 * request goes to a copy that can be supplied. Anything short of that fault includes the copy.
 */
@Singleton
@Order(ItemFilter.SUPPLIER_PREFLIGHT_ORDER)
@AllArgsConstructor
public class ExcludeSupplierThatCannotSupplyItemFilter implements ItemFilter {
	private final SupplierPreflightService supplierPreflightService;

	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		return item -> {
			final var supplierHostLmsCode = getValueOrNull(item, Item::getHostLmsCode);

			if (supplierHostLmsCode == null) {
				return Mono.just(true);
			}

			return supplierPreflightService.checkSupplier(supplierHostLmsCode)
				.map(preflight -> !preflight.cannotSupply())
				.onErrorReturn(true);
		};
	}
}

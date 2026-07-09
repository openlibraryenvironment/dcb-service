package org.olf.dcb.core.interaction.foundation.strategies;

import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRenewal;
import reactor.core.publisher.Mono;

// Strategy interface for circ operations
public interface CirculationStrategy {
	Mono<String> checkOutItem(CheckoutItemCommand command);
	Mono<HostLmsRenewal> renew(HostLmsRenewal renewal);

	Mono<HostLmsItem> getItem(String localItemId);
}

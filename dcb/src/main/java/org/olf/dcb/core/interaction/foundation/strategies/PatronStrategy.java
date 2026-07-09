package org.olf.dcb.core.interaction.foundation.strategies;

import org.olf.dcb.core.interaction.Patron;
import reactor.core.publisher.Mono;

public interface PatronStrategy {
	Mono<Patron> findPatron(String localId);
	Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron);
}

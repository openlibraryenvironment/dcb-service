package org.olf.dcb.request.fulfilment;

import java.util.List;

import reactor.core.publisher.Mono;

public interface PreflightCheck {
	Mono<List<CheckResult>> check(PlacePatronRequestCommand command);
}

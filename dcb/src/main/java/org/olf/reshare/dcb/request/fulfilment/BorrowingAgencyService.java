package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@Prototype
public class BorrowingAgencyService {
	private static final Logger log = LoggerFactory.getLogger(BorrowingAgencyService.class);

	public Mono<PatronRequest> placePatronRequestAtBorrowingAgency(PatronRequest patronRequest) {
		log.debug("placePatronRequestAtBorrowingAgency {}", patronRequest.getId());

		return Mono.just(patronRequest)
			.map(PatronRequest::placedAtBorrowingAgency);
	}
}

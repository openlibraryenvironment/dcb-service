package org.olf.reshare.dcb;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import org.olf.reshare.dcb.test.DcbTest;
import org.olf.reshare.dcb.test.PatronRequestsDataAccess;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DcbTest
public class PatronRequestRepoTest {
	@Inject
	PatronRequestRepository requestRepository;

	@Inject
	SupplierRequestRepository supplierRequestRepository;

	@Inject
	PatronRequestsDataAccess patronRequestsDataAccess;

	@BeforeEach
	void beforeEach() {
		patronRequestsDataAccess.deleteAllPatronRequests();
	}

	@Test
	void patronRequestCanBeSavedWithSupplierRequest() {
		final var patronRequest = new PatronRequest(UUID.randomUUID(),
			"patronId",
			"code",
			UUID.randomUUID(),
			"pickupLocationCode");

		final var supplierRequest = new SupplierRequest(UUID.randomUUID(),
			patronRequest,  UUID.randomUUID(), "holdingAgencyCode");

		final var supplierRequests = List.of(supplierRequest);

		// Save the request
		Mono.from( requestRepository.save(patronRequest) )
			.flatMap(pr -> Flux.fromIterable(supplierRequests)
					.flatMap(supplierRequestRepository::save)
					.then(Mono.just(pr)))
		.block();

		final var fetchedPatronRequest = Mono.from(requestRepository
			.findById(patronRequest.getId())).block();

		assertThat(fetchedPatronRequest, is(notNullValue()));
		
		// Also fetch related supplier requests
		final var fetchedSupplierRequests = Flux.from(supplierRequestRepository
			.findAllByPatronRequest(fetchedPatronRequest))
				.collectList()
				.block();
		
		assertThat(fetchedSupplierRequests.size(), is(1));
		assertThat(fetchedSupplierRequests.get(0).getId(), is(supplierRequest.getId()));
	}
}

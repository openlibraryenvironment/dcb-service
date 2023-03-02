package org.olf.reshare.dcb.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import org.olf.reshare.dcb.test.DcbTest;
import org.olf.reshare.dcb.test.PatronRequestsDataAccess;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DcbTest
class SupplierRequestTests {
	@Inject
	@Client("/")
	HttpClient client;

	@Inject
	PatronRequestRepository patronRequestRepository;

	@Inject
	SupplierRequestRepository supplierRequestRepository;

	@Inject
	PatronRequestsDataAccess patronRequestsDataAccess;

	@BeforeEach
	void beforeEach() {
		patronRequestsDataAccess.deleteAllPatronRequests();
	}

	@Test
	void canGetASupplierRequestViaAdminAPI() {
		final var patronRequest = new PatronRequest(UUID.randomUUID(),
			"patronId",
			"code",
			UUID.randomUUID(),
			"pickupLocationCode");

		final var supplierRequest = new SupplierRequest(UUID.randomUUID(),
			patronRequest,  UUID.randomUUID(), "holdingAgencyCode");

		final var supplierRequests = List.of(supplierRequest);

		// Save the request
		Mono.from( patronRequestRepository.save(patronRequest) )
			.flatMap(pr -> Flux.fromIterable(supplierRequests)
				.flatMap(supplierRequestRepository::save)
				.then(Mono.just(pr)))
			.block();

		final var fetchedPatronRequest = Mono.from(patronRequestRepository
			.findById(patronRequest.getId())).block();

		assertThat(fetchedPatronRequest, is(notNullValue()));

		// Also fetch related supplier requests
		final var fetchedSupplierRequests = Flux.from(supplierRequestRepository
				.findAllByPatronRequest(fetchedPatronRequest))
			.collectList()
			.block();

		assertThat(fetchedSupplierRequests.size(), is(1));
		assertThat(fetchedSupplierRequests.get(0).getId(), is(supplierRequest.getId()));

		UUID uuid = patronRequest.getId();
		assertNotNull(uuid);

		var getResponse = client.toBlocking()
			.retrieve(HttpRequest.GET("/admin/patrons/requests/" + fetchedPatronRequest.getId()),
				PlacedSupplierRequests.class);

		// check the supplier request from admin api is the one we saved
		assertNotNull(getResponse.supplierRequests().get(0));
		assertThat(getResponse.supplierRequests().get(0).id(), is(supplierRequest.getId()));
		assertThat(getResponse.supplierRequests().get(0).agency().code(), is(supplierRequest.getHoldingsAgencyCode()));
		assertThat(getResponse.supplierRequests().get(0).item().id(), is(supplierRequest.getHoldingsItemId()));
	}

	/*
	Expected Response
	 */
	@Serdeable
	public record PlacedSupplierRequests(List<SupplierRequestResponse> supplierRequests) {
		@Serdeable
		public record SupplierRequestResponse(UUID id, ItemResponse item, AgencyResponse agency) {}
		@Serdeable
		public record ItemResponse(UUID id) {}
		@Serdeable
		public record AgencyResponse(String code) { }
	}

}

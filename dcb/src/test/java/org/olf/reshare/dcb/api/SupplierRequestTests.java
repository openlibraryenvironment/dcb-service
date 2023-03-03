package org.olf.reshare.dcb.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

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
import reactor.core.publisher.Mono;

@DcbTest
class SupplierRequestTests {
	@Inject
	PatronRequestRepository patronRequestRepository;

	@Inject
	SupplierRequestRepository supplierRequestRepository;

	@Inject
	PatronRequestsDataAccess patronRequestsDataAccess;

	@Inject
	AdminApiClient adminApiClient;

	@BeforeEach
	void beforeEach() {
		patronRequestsDataAccess.deleteAllPatronRequests();
	}

	@Test
	void canGetASupplierRequestViaAdminAPI() {
		final var patronRequestId = UUID.randomUUID();
		final var bibClusterId = UUID.randomUUID();

		final var patronRequest = new PatronRequest(patronRequestId,
			"bob-jones", "VDR87", bibClusterId,
			"NMB55");

		final var supplierRequestId = UUID.randomUUID();
		final var itemId = UUID.randomUUID();

		final var supplierRequest = new SupplierRequest(supplierRequestId,
			patronRequest, itemId, "BVC67");

		savePatronAndSupplierRequest(patronRequest, supplierRequest);

		final var fetchedPatronRequest = adminApiClient.getPatronRequestViaAdminApi(
			patronRequest.getId());

		assertThat(fetchedPatronRequest, is(notNullValue()));

		assertThat(fetchedPatronRequest.id(), is(patronRequestId));

		assertThat(fetchedPatronRequest.citation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.citation().bibClusterId(), is(bibClusterId));

		assertThat(fetchedPatronRequest.requestor(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().identifier(), is("bob-jones"));

		assertThat(fetchedPatronRequest.requestor().agency(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().agency().code(), is("VDR87"));

		assertThat(fetchedPatronRequest.pickupLocation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.pickupLocation().code(), is("NMB55"));

		assertThat(fetchedPatronRequest.supplierRequests(), is(notNullValue()));
		assertThat(fetchedPatronRequest.supplierRequests(), hasSize(1));

		final var onlySupplierRequest = fetchedPatronRequest.supplierRequests().get(0);

		assertThat(onlySupplierRequest, is(notNullValue()));

		assertThat(onlySupplierRequest.id(), is(supplierRequestId));
		assertThat(onlySupplierRequest.agency().code(), is("BVC67"));
		assertThat(onlySupplierRequest.item().id(), is(itemId));
	}

	private void savePatronAndSupplierRequest(PatronRequest patronRequest,
		SupplierRequest supplierRequest) {

		Mono.from(patronRequestRepository.save(patronRequest))
			.flatMap(pr -> Mono.from(supplierRequestRepository.save(supplierRequest)))
			.block();
	}
}

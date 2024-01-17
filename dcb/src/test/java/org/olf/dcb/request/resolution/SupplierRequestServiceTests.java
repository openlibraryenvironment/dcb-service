package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PLACED;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@DcbTest
public class SupplierRequestServiceTests {
	@Inject
	PatronRequestRepository patronRequestRepository;
	@Inject
	SupplierRequestRepository supplierRequestRepository;
	@Inject
	SupplierRequestService supplierRequestService = new SupplierRequestService(supplierRequestRepository);

	@Test
	void shouldUpdateSupplierRequestAndFetchTheUpdatedSupplierRequest() {
		// Create a new PatronRequest and SupplierRequest
		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.build();

		final var supplierRequestId = randomUUID();
                var initialSupplierRequest = SupplierRequest.builder()
                                             .id(supplierRequestId)
                                             .patronRequest(patronRequest)
                                             .localItemId("itemId")
                                             .localItemBarcode("itemBarcode")
                                             .localItemLocationCode("ItemLocationCode")
                                             .hostLmsCode("supplierHostLmsCode")
                                             .isActive(Boolean.TRUE)
                                             .build();
		// Save the patronRequest and initialSupplierRequest
		Mono.from(patronRequestRepository.save(patronRequest)).block();
		Mono.from(supplierRequestRepository.save(initialSupplierRequest)).block();

		// Set the supplier request values
		initialSupplierRequest.setStatusCode(PLACED);
		initialSupplierRequest.setLocalId("37024897");
		initialSupplierRequest.setLocalStatus("0");

		// Assert the updated values
		assertThat(initialSupplierRequest.getStatusCode(), is(PLACED));
		assertThat(initialSupplierRequest.getLocalId(), is("37024897"));
		assertThat(initialSupplierRequest.getLocalStatus(), is("0"));

		// Update the supplierRequest and retrieve the updated supplierRequest
		Mono.from(supplierRequestService.updateSupplierRequest(initialSupplierRequest)).block();

		// Fetch the supplier requests for the patronRequest and assert the values of the updated supplierRequest
		final var fetchedSupplierRequests = supplierRequestService.findAllSupplierRequestsFor(patronRequest).block();
		final var firstSupplierRequest = fetchedSupplierRequests.get(0);

		// Assert the values of the updated supplierRequest
		assertThat(firstSupplierRequest.getId(), is(supplierRequestId));
		assertThat(firstSupplierRequest.getStatusCode().getDisplayName(), is("PLACED"));
		assertThat(firstSupplierRequest.getLocalId(), is("37024897"));
		assertThat(firstSupplierRequest.getLocalStatus(), is("0"));
	}
}

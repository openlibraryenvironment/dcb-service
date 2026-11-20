package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.SupplierRefusedRequestProblem;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.zalando.problem.ThrowableProblem;

class SupplierPlacementFailureTests {
	private final PatronRequest patronRequest = PatronRequest.builder().id(randomUUID()).build();
	private final PatronIdentity virtualIdentity = PatronIdentity.builder()
		.localId("virtual-patron").localPtype("DCB patrons").build();
	private final SupplierRequest supplierRequest = SupplierRequest.builder()
		.localItemId("item-1").localItemType("Monograph").hostLmsCode("supplier").build();

	@Test
	void aSupplierRefusalIsPassedOnAsItIs() {
		final var refusal = new SupplierRefusedRequestProblem("supplier",
			"supplier's request policy does not allow a Hold request for patron group \"DCB patrons\" on this item",
			"raw response", null, null);

		final var failure = SupplyingAgencyService.placementFailure(
			refusal, patronRequest, virtualIdentity, supplierRequest);

		assertThat(failure, sameInstance(refusal));
	}

	@Test
	void anyOtherFailureIsReportedAsUnableToPlace() {
		final var failure = SupplyingAgencyService.placementFailure(
			new IllegalStateException("boom"), patronRequest, virtualIdentity, supplierRequest);

		assertThat(failure, instanceOf(ThrowableProblem.class));
		assertThat(((ThrowableProblem) failure).getTitle(), startsWith("Unable to place SUPPLIER hold request for pr="));
	}
}

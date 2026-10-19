package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.olf.dcb.core.remediation.RemediationCode.FOLIO_REQUEST_POLICY_REFUSED;

import java.util.Map;

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
	void aFailureThatNamesItsRemediationIsPassedOnAsItIs() {
		final var refusal = new SupplierRefusedRequestProblem("supplier", FOLIO_REQUEST_POLICY_REFUSED,
			Map.of("hostLms", "supplier"), "raw response", null, null);

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

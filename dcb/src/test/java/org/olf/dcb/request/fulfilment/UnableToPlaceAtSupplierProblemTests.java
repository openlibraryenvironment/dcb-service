package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.SupplierRefusedRequestProblem;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;

class UnableToPlaceAtSupplierProblemTests {
	private final PatronRequest patronRequest = PatronRequest.builder().id(randomUUID()).build();
	private final PatronIdentity virtualIdentity = PatronIdentity.builder()
		.localId("virtual-patron").localPtype("DCB patrons").build();
	private final SupplierRequest supplierRequest = SupplierRequest.builder()
		.localItemId("item-1").localItemType("Monograph").hostLmsCode("supplier").build();

	@Test
	void aRefusalIsRecordedInItsOwnWordsWithHowToFixIt() {
		final var refusal = new SupplierRefusedRequestProblem("The supplier's policy refuses it",
			"Allow it in the supplier's policy", "raw response", null, null);

		final var problem = SupplyingAgencyService.unableToPlaceRequestAtSupplyingAgencyProblem(
			refusal, patronRequest, virtualIdentity, supplierRequest);

		assertThat(problem.getTitle(), is("The supplier's policy refuses it"));
		assertThat(problem.getParameters(), hasEntry("howToFix", "Allow it in the supplier's policy"));
		assertThat(problem.getParameters(), hasEntry("supplier-dcbLocalItemId", "item-1"));
	}

	@Test
	void anyOtherFailureKeepsTheGenericTitle() {
		final var problem = SupplyingAgencyService.unableToPlaceRequestAtSupplyingAgencyProblem(
			new IllegalStateException("boom"), patronRequest, virtualIdentity, supplierRequest);

		assertThat(problem.getTitle(), startsWith("Unable to place SUPPLIER hold request for pr="));
		assertThat(problem.getParameters(), not(hasKey("howToFix")));
	}
}

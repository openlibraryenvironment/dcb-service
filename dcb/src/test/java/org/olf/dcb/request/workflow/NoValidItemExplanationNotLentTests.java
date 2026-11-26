package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.SupplierPreflight;
import org.olf.dcb.core.model.DerivedLoanPolicy;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.SupplierPreflightService;
import org.olf.dcb.storage.PatronRequestAuditRepository;

class NoValidItemExplanationNotLentTests {
	private final NoValidItemExplanation explanation = new NoValidItemExplanation(
		mock(SupplierPreflightService.class), mock(PatronRequestAuditService.class),
		mock(PatronRequestAuditRepository.class));

	@Test
	void aCopyItsLibraryDoesNotLendIsNamedWithTheReasonItsSystemGave() {
		final var faults = explanation.notLentBy(List.of(
			item("POLARIS-A", DerivedLoanPolicy.NO_LEND, "LoanPolicy NO_LEND because the item is not holdable"),
			item("POLARIS-A", DerivedLoanPolicy.LOCAL_ONLY, "LoanPolicy LOCAL_ONLY because the item is not loanable outside the system")));

		assertThat(faults, hasSize(1));
		assertThat(faults.get(0).supplierHostLmsCode(), is("POLARIS-A"));
		assertThat(faults.get(0).preflight().reason(), is("LoanPolicy NO_LEND because the item is not holdable"));
	}

	@Test
	void aCopyExcludedForAnyOtherReasonIsNotNamed() {
		assertThat(explanation.notLentBy(List.of(item("SIERRA-B", DerivedLoanPolicy.GENERAL, "Excluded by something"))),
			is(empty()));
	}

	@Test
	void aSupplierFaultComesBeforeALibraryThatDoesNotLendAndEachLibraryAppearsOnce() {
		final var fault = new NoValidItemExplanation.Fault("POLARIS-A", SupplierPreflight.cannotSupply("unreachable", "ping it"));
		final var notLent = explanation.notLentBy(List.of(
			item("POLARIS-A", DerivedLoanPolicy.NO_LEND, "LoanPolicy NO_LEND because the item is non-circulating"),
			item("POLARIS-C", DerivedLoanPolicy.REFERENCE_ONLY, "LoanPolicy derived by taking value of REF for shelving location")));

		final var merged = NoValidItemExplanation.withNotLent(List.of(fault), notLent);

		assertThat(merged.stream().map(NoValidItemExplanation.Fault::supplierHostLmsCode).toList(),
			contains("POLARIS-A", "POLARIS-C"));
		assertThat(merged.get(0).preflight().reason(), is("unreachable"));
	}

	private static Item item(String hostLmsCode, DerivedLoanPolicy policy, String decision) {
		return Item.builder()
			.agency(onSystem(hostLmsCode))
			.derivedLoanPolicy(policy)
			.decisionLogEntries(List.of(decision))
			.build();
	}

	private static DataAgency onSystem(String hostLmsCode) {
		return DataAgency.builder().code("agency").hostLms(DataHostLms.builder().code(hostLmsCode).build()).build();
	}
}

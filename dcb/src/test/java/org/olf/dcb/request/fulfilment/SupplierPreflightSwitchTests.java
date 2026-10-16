package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.core.interaction.SupplierPreflight.Outcome.NOT_CHECKED;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

/** Off unless DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED=true: a deployment opts in once it has checked its pings. */
@DcbTest
class SupplierPreflightSwitchTests {
	@Inject
	private SupplierPreflightService supplierPreflightService;

	@Test
	void shouldCheckNoSupplierUnlessTurnedOn() {
		assertThat(supplierPreflightService.checkSupplier("UNKNOWN-HOST-LMS").block().outcome(), is(NOT_CHECKED));
	}
}

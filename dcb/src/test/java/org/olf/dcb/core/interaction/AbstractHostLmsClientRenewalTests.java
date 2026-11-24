package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;

/**
 * An adapter that never implemented renewal must refuse, not stay silent.
 *
 * The inherited default returned an empty Mono, and SupplierRenewalTransition runs neither
 * its success nor its failure branch on empty — so a renewal against such an adapter
 * produced no renewal, no failure and no audit entry. ORSApplianceHostLMS is the
 * implementation that inherits this.
 */
class AbstractHostLmsClientRenewalTests {

	@Test
	@DisplayName("The inherited renewal refuses and names the Host LMS")
	void inheritedRenewalRefusesRatherThanReturningEmpty() {
		final var client = clientForHostLmsWithoutRenewalSupport("appliance-1");

		final var error = assertThrows(UnsupportedOperationException.class,
			() -> singleValueFrom(client.renew(HostLmsRenewal.builder()
				.localItemId("item-1")
				.localPatronId("patron-1")
				.build())));

		// Naming the system is the whole diagnostic: the audit entry is otherwise
		// indistinguishable from any other renewal failure.
		assertThat(error.getMessage(), containsString("appliance-1"));
	}

	private static AbstractHostLmsClient clientForHostLmsWithoutRenewalSupport(String code) {
		return new AbstractHostLmsClient(DataHostLms.builder().code(code).build()) {
		};
	}
}

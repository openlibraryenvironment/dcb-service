package org.olf.dcb.core.api.discovery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

/**
 * A discovery request either lets DCB choose the copy or names one completely: an incomplete
 * choice is dropped by PatronRequestService, which would then resolve a different copy.
 */
class DiscoveryCopySelectionTests {

	@Test
	void noCopyLetsDcbChoose() {
		assertThat(DiscoveryPatronRequestsController.chosenCopy(null, null, null), is(nullValue()));
		assertThat(DiscoveryPatronRequestsController.chosenCopy("", " ", null), is(nullValue()));
	}

	@Test
	void aCompleteCopyIsTheCopyRequested() {
		final var copy = DiscoveryPatronRequestsController.chosenCopy("item-1", "COLLEGE_LMS", "college");

		assertThat(copy.getLocalId(), is("item-1"));
		assertThat(copy.getLocalSystemCode(), is("COLLEGE_LMS"));
		assertThat(copy.getAgencyCode(), is("college"));
	}

	@Test
	void anIncompleteCopyIsRefusedRatherThanReplaced() {
		final var refused = assertThrows(HttpStatusException.class,
			() -> DiscoveryPatronRequestsController.chosenCopy("item-1", "COLLEGE_LMS", null));

		assertThat(refused.getStatus(), is(HttpStatus.BAD_REQUEST));
	}
}

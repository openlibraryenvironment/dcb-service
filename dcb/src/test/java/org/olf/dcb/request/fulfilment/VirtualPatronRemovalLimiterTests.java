package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

class VirtualPatronRemovalLimiterTests {
	@Test
	void aMemberOfStaffMayRemoveTenVirtualPatronsAMinuteAndNoMore() {
		final var limiter = new VirtualPatronRemovalLimiter();

		for (int removal = 0; removal < 10; removal++) {
			assertThat("removal " + (removal + 1), limiter.tryAcquireForCaller("staff"), is(true));
		}

		assertThat(limiter.tryAcquireForCaller("staff"), is(false));
		assertThat("another member of staff has their own ten", limiter.tryAcquireForCaller("other staff"), is(true));
	}
}

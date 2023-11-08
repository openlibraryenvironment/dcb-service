package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
class PatronRequestPreflightChecksServiceTests {
	@Inject
	private PatronRequestPreflightChecksService preflightChecksService;

	@Test
	void shouldBeInstantiated() {
		assertThat("Service instance should not be null", preflightChecksService, is(notNullValue()));
	}
}

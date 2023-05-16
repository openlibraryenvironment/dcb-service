package org.olf.reshare.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
class PatronTypeServiceTests {
	@Inject
	private PatronTypeService patronTypeService;

	@Test
	void fixedPatronTypeHasDefaultValue() {
		assertThat(patronTypeService.determinePatronType(), is(210));
	}
}

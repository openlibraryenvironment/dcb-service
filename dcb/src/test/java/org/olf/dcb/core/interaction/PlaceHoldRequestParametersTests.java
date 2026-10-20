package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

class PlaceHoldRequestParametersTests {
	@Test
	void textFormLeavesOutThePatronsBarcodeAndNames() {
		final var parameters = PlaceHoldRequestParameters.builder()
			.localPatronId("patron-id-1")
			.localPatronBarcode("BARCODE-87654321")
			.localNames("Alex Example")
			.localItemId("item-id-1")
			.build();

		final var text = parameters.toString();

		assertThat(text, not(containsString("BARCODE-87654321")));
		assertThat(text, not(containsString("Alex Example")));
		assertThat(text, containsString("patron-id-1"));
		assertThat(text, containsString("item-id-1"));
	}
}

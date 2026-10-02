package org.olf.dcb.dataimport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

@DcbTest
@Property(name = "dcb.source-import.vanished-max-share", value = "0.25")
class SourceImportPropertiesTests {
	@Inject
	private SourceImportProperties configured;

	@Test
	void shouldBindTheVanishedLimitFromConfiguration() {
		assertThat(configured.getVanishedMaxShare(), is(0.25));
	}

	@Test
	void shouldDefaultTheVanishedLimitToATenth() {
		assertThat(new SourceImportProperties().getVanishedMaxShare(), is(0.10));
	}

	@Test
	void shouldRejectAVanishedLimitAboveOne() {
		final var error = assertThrows(IllegalArgumentException.class,
			() -> new SourceImportProperties().setVanishedMaxShare(10));

		assertThat(error.getMessage(), containsString("dcb.source-import.vanished-max-share"));
	}

	@Test
	void shouldRejectANegativeVanishedLimit() {
		assertThrows(IllegalArgumentException.class,
			() -> new SourceImportProperties().setVanishedMaxShare(-0.1));
	}
}

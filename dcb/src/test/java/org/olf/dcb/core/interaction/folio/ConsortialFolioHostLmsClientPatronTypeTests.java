package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;

@DcbTest
class ConsortialFolioHostLmsClientPatronTypeTests {
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	public void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldDetermineCanonicalPatronTypeBasedUponLocalPatronType() {
		// Arrange
		final var hostLms = createFolioHostLms();

		referenceValueMappingFixture.definePatronTypeMapping(hostLms.getCode(),
			"local-patron-type", "DCB", "canonical-patron-type");

		// Act
		final var client = hostLmsFixture.createClient(hostLms.getCode());

		final var canonicalPatronType = singleValueFrom(
			client.findCanonicalPatronType("local-patron-type", null));

		// Assert
		assertThat(canonicalPatronType, is("canonical-patron-type"));
	}

	@Test
	void shouldFailToDetermineCanonicalPatronTypeWhenNoMappingIsDefined() {
		// Arrange
		final var hostLms = createFolioHostLms();

		// Act
		final var client = hostLmsFixture.createClient(hostLms.getCode());

		final var exception = assertThrows(NoPatronTypeMappingFoundException.class,
			() -> singleValueFrom(client.findCanonicalPatronType("local-patron-type", null)));

		// Assert
		assertThat(exception, hasMessage(
			"Unable to map patron type \"local-patron-type\" on Host LMS: \"folio-host-lms\" to canonical value"));
	}

	private DataHostLms createFolioHostLms() {
		return hostLmsFixture.createFolioHostLms("folio-host-lms",
			"http://some-folio-system", "", "", "");
	}
}

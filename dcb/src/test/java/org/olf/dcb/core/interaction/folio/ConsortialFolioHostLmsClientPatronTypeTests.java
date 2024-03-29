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
	void shouldDetermineLocalPatronTypeBasedUponDirectMapping() {
		// Arrange
		final var hostLms = createFolioHostLms();

		referenceValueMappingFixture.definePatronTypeMapping( "DCB", "canonical-patron-type", hostLms.getCode(), "local-patron-type");
		// referenceValueMappingFixture.definePatronTypeMapping(hostLms.getCode(), "local-patron-type", "DCB", "canonical-patron-type");

		// Act
		final var client = hostLmsFixture.createClient(hostLms.getCode());

		final var localPatronType = singleValueFrom(
			client.findLocalPatronType("canonical-patron-type"));

		// Assert
		assertThat(localPatronType, is("local-patron-type"));
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

	@Test
	void shouldFailToDetermineLocalPatronTypeWhenNoMappingIsDefined() {
		// Arrange
		final var hostLms = createFolioHostLms();

		// Act
		final var client = hostLmsFixture.createClient(hostLms.getCode());

		final var exception = assertThrows(NoPatronTypeMappingFoundException.class,
			() -> singleValueFrom(client.findLocalPatronType("canonical-patron-type")));

		// Assert
		assertThat(exception, hasMessage(
			"Unable to map canonical patron type \"canonical-patron-type\" to a patron type on Host LMS: \"folio-host-lms\""));
	}

	@Test
	void shouldFailToDetermineLocalPatronWithoutMapping() {
		// This is counter-intuitive
		// At the moment, this only checks for a mapping from local to canonical
		// and uses the source value (rather than the target value)
		//
		// Meaning it will fail even if a canonical to local mapping is defined
		//
		// This is intended to deter folks from defining duplicate mappings in both directions

		// Arrange
		final var hostLms = createFolioHostLms();

		// Deliberately don't provider the mapping
		// referenceValueMappingFixture.definePatronTypeMapping( "DCB", "canonical-patron-type", hostLms.getCode(), "local-patron-type");

		// Act
		final var client = hostLmsFixture.createClient(hostLms.getCode());

		final var exception = assertThrows(NoPatronTypeMappingFoundException.class, () -> singleValueFrom(client.findLocalPatronType("canonical-patron-type")));

		// Assert
		assertThat(exception, hasMessage( "Unable to map canonical patron type \"canonical-patron-type\" to a patron type on Host LMS: \"folio-host-lms\""));
	}

	private DataHostLms createFolioHostLms() {
		return hostLmsFixture.createFolioHostLms("folio-host-lms",
			"http://some-folio-system", "", "", "");
	}
}

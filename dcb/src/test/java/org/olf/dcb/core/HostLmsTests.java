package org.olf.dcb.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.polaris.papi.PAPILmsClient;
import org.olf.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;

@DcbTest
class HostLmsTests {
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private HostLmsService hostLmsService;

	@BeforeEach
	void beforeEach() {
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldFindHostLmsInDatabaseByCode() {
		// Arrange
		hostLmsFixture.saveHostLms(new DataHostLms(UUID.randomUUID(), "database-host",
			"Database Host", SierraLmsClient.class.getName(), Map.of()));

		// Act
		final var foundHost = hostLmsService.findByCode("database-host").block();

		// Assert
		assertThat(foundHost, hasNonNullId());
		assertThat(foundHost, hasCode("database-host"));
		assertThat(foundHost, hasName("Database Host"));
		assertThat(foundHost, hasType(SierraLmsClient.class));
	}

	@Test
	void shouldFindHostLmsInDatabaseById() {
		// Arrange
		final var hostLmsId = UUID.randomUUID();

		hostLmsFixture.saveHostLms(new DataHostLms(hostLmsId, "database-host",
			"Database Host", SierraLmsClient.class.getName(), Map.of()));

		final var foundHost = hostLmsService.findById(hostLmsId).block();

		assertThat(foundHost, hasId(hostLmsId));
		assertThat(foundHost, hasCode("database-host"));
		assertThat(foundHost, hasName("Database Host"));
		assertThat(foundHost, hasType(SierraLmsClient.class));
	}

	@Test
	void shouldNotFindHostLmsByIdWhenUnknown() {
		// Arrange
		// Host LMS that should not be found
		hostLmsFixture.saveHostLms(new DataHostLms(UUID.randomUUID(), "database-host",
			"Database Host", SierraLmsClient.class.getName(), Map.of()));

		// Act
		final var unknownHostId = UUID.randomUUID();

		final var exception = assertThrows(HostLmsService.UnknownHostLmsException.class,
			() -> hostLmsService.findById(unknownHostId).block());

		// Assert
		assertThat(exception, hasMessage("No Host LMS found for ID: " + unknownHostId));
	}

	@Test
	void shouldNotFindHostLmsByCodeWhenUnknown() {
		// Arrange
		// Host LMS that should not be found
		hostLmsFixture.saveHostLms(new DataHostLms(UUID.randomUUID(), "database-host",
			"Database Host", SierraLmsClient.class.getName(), Map.of()));

		// Act
		final var exception = assertThrows(HostLmsService.UnknownHostLmsException.class,
			() -> hostLmsService.findByCode("unknown-host").block());

		// Assert
		assertThat(exception, hasMessage("No Host LMS found for code: unknown-host"));
	}

	@Nested
	class SierraDatabaseHostLmsTests {
		@Test
		void shouldBeAbleToCreateSierraClientFromDatabaseHostLms() {
			// Arrange
			hostLmsFixture.createSierraHostLms("sierra-database-host-lms", "some-username",
				"some-password", "https://some-sierra-system");

			// Act
			final var client = hostLmsFixture.createClient("sierra-database-host-lms");

			// Assert
			assertThat(client, is(instanceOf(SierraLmsClient.class)));
		}
		@Test
		void shouldBeAbleToCreateSierraIngestSourceFromDatabaseHostLms() {
			// Arrange
			hostLmsFixture.createSierraHostLms("sierra-database-host-lms", "some-username",
				"some-password", "https://some-sierra-system");

			// Act
			final var client = hostLmsFixture.getIngestSource("sierra-database-host-lms");

			// Assert
			assertThat(client, is(instanceOf(SierraLmsClient.class)));
		}
	}

	@Test
	void shouldBeAbleToCreatePolarisClientFromDatabaseHostLms() {
		// Arrange
		hostLmsFixture.createPolarisHostLms("polaris-database-host-lms", "some-username",
			"some-password", "https://some-polaris-system", "some-domain",
			"some-access-id", "some-access-key");

		// Act
		final var client = hostLmsFixture.createClient("polaris-database-host-lms");

		// Assert
		assertThat(client, is(instanceOf(PAPILmsClient.class)));
	}

	@Test
	void shouldBeAbleToCreatePolarisIngestSourceFromDatabaseHostLms() {
		// Arrange
		hostLmsFixture.createPolarisHostLms("polaris-database-host-lms", "some-username",
			"some-password", "https://some-polaris-system", "some-domain",
			"some-access-id", "some-access-key");

		// Act
		final var client = hostLmsFixture.getIngestSource("polaris-database-host-lms");

		// Assert
		assertThat(client, is(instanceOf(PAPILmsClient.class)));
	}

	private static Matcher<DataHostLms> hasId(UUID hostLmsId) {
		return hasProperty("id", is(hostLmsId));
	}

	private static Matcher<DataHostLms> hasNonNullId() {
		return hasProperty("id", notNullValue());
	}

	private static Matcher<DataHostLms> hasName(String expectedName) {
		return hasProperty("name", is(expectedName));
	}

	private static Matcher<DataHostLms> hasCode(String expectedCode) {
		return hasProperty("code", is(expectedCode));
	}

	private static Matcher<DataHostLms> hasType(Class<SierraLmsClient> expectedType) {
		return hasProperty("type", is(expectedType));
	}

	private static Matcher<Throwable> hasMessage(String expectedMessage) {
		return hasProperty("message", is(expectedMessage));
	}
}

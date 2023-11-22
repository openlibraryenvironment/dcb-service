package org.olf.dcb.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasClientClass;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasCode;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasId;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasIngestSourceClass;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasName;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasNoIngestSourceClass;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasNonNullId;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.folio.FolioLmsClient;
import org.olf.dcb.core.interaction.folio.FolioOaiPmhIngestSource;
import org.olf.dcb.core.interaction.polaris.PolarisLmsClient;
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
		hostLmsFixture.saveHostLms(DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("database-host")
			.name("Database Host")
			.lmsClientClass(SierraLmsClient.class.getCanonicalName())
			.ingestSourceClass(SierraLmsClient.class.getCanonicalName())
			.clientConfig(Map.of())
			.build());

		// Act
		final var foundHost = hostLmsService.findByCode("database-host").block();

		// Assert
		assertThat(foundHost, hasNonNullId());
		assertThat(foundHost, hasCode("database-host"));
		assertThat(foundHost, hasName("Database Host"));
		assertThat(foundHost, hasClientClass(SierraLmsClient.class.getCanonicalName()));
		assertThat(foundHost, hasIngestSourceClass(SierraLmsClient.class.getCanonicalName()));
	}

	@Test
	void shouldFindHostLmsInDatabaseById() {
		// Arrange
		final var hostLmsId = UUID.randomUUID();

		hostLmsFixture.saveHostLms(DataHostLms.builder()
			.id(hostLmsId)
			.code("database-host")
			.name("Database Host")
			.lmsClientClass(SierraLmsClient.class.getCanonicalName())
			.clientConfig(Map.of())
			.build());

		final var foundHost = hostLmsService.findById(hostLmsId).block();

		assertThat(foundHost, hasId(hostLmsId));
		assertThat(foundHost, hasCode("database-host"));
		assertThat(foundHost, hasName("Database Host"));
		assertThat(foundHost, hasClientClass(SierraLmsClient.class.getCanonicalName()));
		assertThat(foundHost, hasNoIngestSourceClass());
	}

	@Test
	void shouldNotFindHostLmsByIdWhenUnknown() {
		// Arrange
		// Host LMS that should not be found
		hostLmsFixture.saveHostLms(DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("database-host")
			.name("Database Host")
			.lmsClientClass(SierraLmsClient.class.getName())
			.clientConfig(Map.of())
			.build());

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
		hostLmsFixture.saveHostLms(DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("database-host")
			.name("Database Host")
			.lmsClientClass(SierraLmsClient.class.getName())
			.clientConfig(Map.of())
			.build());

		// Act
		final var exception = assertThrows(HostLmsService.UnknownHostLmsException.class,
			() -> hostLmsService.findByCode("unknown-host").block());

		// Assert
		assertThat(exception, hasMessage("No Host LMS found for code: unknown-host"));
	}

	@Nested
	class SierraDatabaseHostLmsTests {
		@BeforeEach
		void beforeEach() {
			hostLmsFixture.createSierraHostLms("sierra-database-host-lms", "some-username",
				"some-password", "https://some-sierra-system");
		}

		@Test
		void shouldBeAbleToCreateSierraClientFromDatabaseHostLms() {
			// Act
			final var client = hostLmsFixture.createClient("sierra-database-host-lms");

			// Assert
			assertThat(client, is(instanceOf(SierraLmsClient.class)));
		}

		@Test
		void shouldBeAbleToCreateSierraIngestSourceFromDatabaseHostLms() {
			// Act
			final var client = hostLmsFixture.getIngestSource("sierra-database-host-lms");

			// Assert
			assertThat(client, is(instanceOf(SierraLmsClient.class)));
		}
	}

	@Nested
	class PolarisDatabaseHostLmsTests {
		@BeforeEach
		void beforeEach() {
			hostLmsFixture.createPolarisHostLms("polaris-database-host-lms", "some-username",
				"some-password", "https://some-polaris-system", "some-domain",
				"some-access-id", "some-access-key");
		}

		@Test
		void shouldBeAbleToCreatePolarisClientFromDatabaseHostLms() {
			// Act
			final var client = hostLmsFixture.createClient("polaris-database-host-lms");

			// Assert
			assertThat(client, is(instanceOf(PolarisLmsClient.class)));
		}

		@Test
		void shouldBeAbleToCreatePolarisIngestSourceFromDatabaseHostLms() {
			// Act
			final var client = hostLmsFixture.getIngestSource("polaris-database-host-lms");

			// Assert
			assertThat(client, is(instanceOf(PolarisLmsClient.class)));
		}
	}

	@Nested
	class FolioDatabaseHostLmsTests {
		@BeforeEach
		void beforeEach() {
			hostLmsFixture.createFolioHostLms("folio-database-host-lms",
				"https://some-folio-system",
				"some-api-key", "some-record-syntax", "some-metadata-prefix");
		}

		@Test
		void shouldBeAbleToCreateFolioClientFromDatabaseHostLms() {
			// Act
			final var client = hostLmsFixture.createClient("folio-database-host-lms");

			// Assert
			assertThat(client, is(instanceOf(FolioLmsClient.class)));
		}

		@Test
		void shouldBeAbleToCreateFolioIngestSourceFromDatabaseHostLms() {
			// Act
			final var client = hostLmsFixture.getIngestSource("folio-database-host-lms");

			// Assert
			assertThat(client, is(instanceOf(FolioOaiPmhIngestSource.class)));
		}
	}
}

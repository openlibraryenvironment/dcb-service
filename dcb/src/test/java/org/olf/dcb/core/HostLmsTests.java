package org.olf.dcb.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
		assertThat(foundHost, is(notNullValue()));

		assertThat(foundHost.getId(), is(notNullValue()));
		assertThat(foundHost.getCode(), is("database-host"));
		assertThat(foundHost.getName(), is("Database Host"));
		assertThat(foundHost.getType(), is(SierraLmsClient.class));
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
		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("No Host LMS found for code: unknown-host"));
	}

	@Test
	void shouldFindHostLmsInDatabaseById() {
		// Arrange
		final var hostLmsId = UUID.randomUUID();

		hostLmsFixture.saveHostLms(new DataHostLms(hostLmsId, "database-host",
			"Database Host", SierraLmsClient.class.getName(), Map.of()));

		final var foundHost = hostLmsService.findById(hostLmsId).block();

		assertThat(foundHost, is(notNullValue()));

		assertThat(foundHost.getId(), is(hostLmsId));
		assertThat(foundHost.getCode(), is("database-host"));
		assertThat(foundHost.getName(), is("Database Host"));
		assertThat(foundHost.getType(), is(SierraLmsClient.class));
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
		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("No Host LMS found for ID: " + unknownHostId));
	}
}

package org.olf.reshare.dcb.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static services.k_int.interaction.sierra.SierraTestUtils.mockFor;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import org.olf.reshare.dcb.test.HostLmsFixture;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(rebuildContext = true, transactional = false, propertySources = { "classpath:tests/hostLmsProps.yml" })
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class HostLmsTests {
	@Inject
	private HostLmsRepository hostLmsRepository;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeAll
	static void addFakeSierraApis(MockServerClient mock) {
		// Mock login to sierra
		mockFor(mock, "test1.com")
			.setValidCredentials("test1-key", "test1-secret", "test1_auth_token", 3000L);

		mockFor(mock, "test2.com")
			.setValidCredentials("test2-key2", "test2-secret", "test2_auth_token", 3600L);

		// Mock the response from Sierra
		mock.when(request().withHeader("Accept", "application/json").withHeader("host", "test1.com")
			.withHeader("Authorization", "Bearer test1_auth_token").withMethod("GET").withPath("/iii/sierra-api/v6/bibs/*"))

		.respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
				.withBody(json("{" + "	\"total\": 2," + "	\"start\": 0," + "	\"entries\": [" + "		{"
					+ "			\"id\": \"id1\"," + "			\"deleted\": false," + "			\"title\": \"Test1 record 1\","
					+ "			\"author\": \"Wojciechowska, Maia,\"," + "			\"publishYear\": 1969" + "		}," + "		{"
					+ "			\"id\": \"id2\"," + "			\"deleted\": false," + "			\"title\": \"Test1 record 2\","
					+ "			\"author\": \"Smith, John,\"," + "			\"publishYear\": 2010" + "		}" + "	]" + "}")));

		// 2nd target
		mock.when(request().withHeader("Accept", "application/json").withHeader("host", "test2.com")
			.withHeader("Authorization", "Bearer test2_auth_token").withMethod("GET").withPath("/iii/sierra-api/v6/bibs/*"))
			.respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
					.withBody(json("{\n" + "	\"total\": 1,\n" + "	\"start\": 0,\n" + "	\"entries\": [\n" + "		{\n"
						+ "			\"id\": \"1\",\n" + "			\"deleted\": false,\n" + "			\"title\": \"Test2 record\",\n"
						+ "			\"author\": \"Wojciechowska, Maia,\",\n" + "			\"publishYear\": 1969\n" + "		}\n" + "	]\n"
						+ "}")));
	}

	@BeforeEach
	void beforeEach() {
		// Care is needed here - hostLMS records from config are now converted into DB entries by a bootstrap/startup class.
		// This delete will wipe out any config set up as a part of app initiailisation so your tests here must rely upon
		// manually created hostLms entries
		hostLmsFixture.deleteAllHostLMS();
	}

	@Inject
	ResourceLoader loader;

	@Inject
	HostLmsService manager;

	@Test
	void shouldFindHostInDatabaseByCode() {
		Mono.from(hostLmsRepository.save(new DataHostLms(UUID.randomUUID(), "database-host",
			"Database Host", SierraLmsClient.class.getName(), Map.of())))
		.block();

		final var foundHost = manager.findByCode("database-host").block();

		assertThat(foundHost, is(notNullValue()));

		assertThat(foundHost.getId(), is(notNullValue()));
		assertThat(foundHost.getCode(), is("database-host"));
		assertThat(foundHost.getName(), is("Database Host"));
		assertThat(foundHost.getType(), is(SierraLmsClient.class));
	}

	@Test
	void shouldNotFindHostByCodeWhenUnknown() {
		Mono.from(hostLmsRepository.save(new DataHostLms(UUID.randomUUID(), "database-host",
				"Database Host", SierraLmsClient.class.getName(), Map.of())))
			.block();

		final var exception = assertThrows(HostLmsService.UnknownHostLmsException.class,
			() -> manager.findByCode("unknown-host").block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("No Host LMS found for code: unknown-host"));
	}

	@Test
	void shouldFindHostInDatabaseById() {
		final var hostId = UUID.randomUUID();

		Mono.from(hostLmsRepository.save(new DataHostLms(hostId, "database-host",
				"Database Host", SierraLmsClient.class.getName(), Map.of())))
			.block();

		final var foundHost = manager.findById(hostId).block();

		assertThat(foundHost, is(notNullValue()));

		assertThat(foundHost.getId(), is(hostId));
		assertThat(foundHost.getCode(), is("database-host"));
		assertThat(foundHost.getName(), is("Database Host"));
		assertThat(foundHost.getType(), is(SierraLmsClient.class));
	}

	@Test
	void shouldNotFindHostByIdWhenUnknown() {
		Mono.from(hostLmsRepository.save(new DataHostLms(UUID.randomUUID(), "database-host",
				"Database Host", SierraLmsClient.class.getName(), Map.of())))
			.block();

		final var unknownHostId = UUID.randomUUID();

		final var exception = assertThrows(HostLmsService.UnknownHostLmsException.class,
			() -> manager.findById(unknownHostId).block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("No Host LMS found for ID: " + unknownHostId));
	}
}

package org.olf.reshare.dcb.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import org.olf.reshare.dcb.test.DataAccess;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(rebuildContext = true, transactional = false, propertySources = { "classpath:tests/hostLmsProps.yml" })
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class HostLmsTests {
	private final DataAccess dataAccess = new DataAccess();

	@Inject
	private HostLmsRepository hostLmsRepository;

	@BeforeAll
	static void addFakeSierraApis(MockServerClient mock) {

		// Mock login to sierra
		SierraTestUtils.mockFor(mock, "test1.com").setValidCredentials("test1-key", "test1-secret", "test1_auth_token",
				3000L);

		SierraTestUtils.mockFor(mock, "test2.com").setValidCredentials("test2-key2", "test2-secret", "test2_auth_token",
				3600L);

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
		dataAccess.deleteAll(hostLmsRepository.findAll(),
			hostLms -> hostLmsRepository.delete(hostLms.getId()));
	}

	@Inject
	ResourceLoader loader;

	@Inject
	HostLmsService manager;

	@Test
	void hostLmsFromConfigLoaded() {
		// Get the list of configured host LMS
		List<HostLms> allLms = manager.getAllHostLms().sort(
				Comparator.comparing(HostLms::getName))
			.collect(Collectors.toUnmodifiableList()).block();

		assertEquals(2, allLms.size());

		final HostLms test1 = allLms.get(0);
//		assertEquals(2, test1.getAgencies().size());

		final HostLms test2 = allLms.get(1);
//		assertEquals(2, test2.getAgencies().size());

		List<Map<String, ?>> results = manager.getClientFor(test1).flatMapMany(client -> client.getAllBibData())
				.collect(Collectors.toList()).block();

		assertEquals(2, results.size());

		results = manager.getClientFor(test2).flatMapMany(client -> client.getAllBibData()).collect(Collectors.toList())
				.block();

		assertEquals(1, results.size());
	}

	@Test
	void shouldFindHostInConfigByCode() {
		final var foundHost = manager.findByCode("test1").block();

		assertThat(foundHost, is(notNullValue()));

		assertThat(foundHost.getId(), is(notNullValue()));
		assertThat(foundHost.getCode(), is("test1"));
		assertThat(foundHost.getName(), is("test1"));
		assertThat(foundHost.getType(), is(SierraLmsClient.class));
	}

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
	void shouldNotFindHostWhenUnknown() {
		Mono.from(hostLmsRepository.save(new DataHostLms(UUID.randomUUID(), "database-host",
				"Database Host", SierraLmsClient.class.getName(), Map.of())))
			.block();

		final var exception = assertThrows(RuntimeException.class,
			() -> manager.findByCode("unknown-host").block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("No Host LMS found for code: unknown-host"));
	}
}

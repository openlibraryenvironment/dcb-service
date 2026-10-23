package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.test.HostLmsFixture;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class CreateKohaHostLmsTests {
	private static final String CODE = "koha-create";
	private static final String CREATE = """
		mutation($input: CreateHostLmsInput!) {
			createHostLms(input: $input) { hostLms { code } }
		}""";

	@Inject
	private GraphQL graphQL;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private HostLmsRepository hostLmsRepository;

	private MockServerClient mockServerClient;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();
	}

	@AfterAll
	void afterAll() {
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldRefuseAnNcipFallbackWithoutItsToken() {
		final var config = kohaConfig();
		config.put("ncip-checkin-fallback", "true");
		config.put("ncip-checkin-url", "https://fake-koha-create/api/v1/contrib/ncip_server/ncip");

		final var result = create(config);

		assertThat(result.getErrors(), hasSize(1));
		assertThat(result.getErrors().get(0).getMessage(), containsString("ncip-checkin-token"));
		assertThat(Mono.from(hostLmsRepository.findByCode(CODE)).blockOptional().isPresent(), is(false));
	}

	@Test
	void shouldCreateAKohaWhoseNcipFallbackIsOff() {
		final var config = kohaConfig();
		config.put("ncip-checkin-fallback", "false");

		final var result = create(config);

		assertThat(result.getErrors(), is(empty()));
		final Map<String, Map<String, Map<String, Object>>> data = result.getData();
		assertThat(data.get("createHostLms").get("hostLms").get("code"), is(CODE));
	}

	private ExecutionResult create(Map<String, Object> clientConfig) {
		return graphQL.execute(ExecutionInput.newExecutionInput()
			.query(CREATE)
			.variables(Map.of("input", Map.of(
				"code", CODE,
				"name", "Koha create",
				"lmsClientClass", "org.olf.dcb.core.interaction.koha.KohaHostLmsClient",
				"clientConfig", clientConfig)))
			.graphQLContext(Map.of("roles", List.of("CONSORTIUM_ADMIN"), "userName", "create-koha-tests"))
			.build());
	}

	private static Map<String, Object> kohaConfig() {
		final Map<String, Object> config = new HashMap<>();
		config.put("api-url", "https://fake-koha-create");
		config.put("client_id", "any-client-id");
		config.put("client_secret", "any-client-secret");
		config.put("default-agency-code", "any-agency");
		config.put("sharing-library-code", "DCB");
		config.put("virtual-item-library-code", "DCB");
		config.put("ingest", false);
		return config;
	}
}

package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpRequest.request;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

/** The token's own details prove the key, and say which of the permissions DCB uses it lacks. */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraPingTests {
	private static final String HOST_LMS_CODE = "sierra-ping";
	private static final String BASE_URL = "https://sierra-ping-tests.com";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockServerClient mockServerClient;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, "key", "secret", BASE_URL, "item");
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials("key", "secret", "token", 3600);
	}

	@Test
	void shouldReportTheTokenLifetimeAndThePermissionsItLacks() {
		final var allButHolds = new ArrayList<>(SierraLmsClient.PERMISSIONS_DCB_USES);
		allButHolds.remove("Patrons_Hold_Request_Create");

		tokenInfoGrants(allButHolds);

		final var response = ping();

		assertThat(response.getStatus(), is(PingResponse.OK));
		assertThat(response.getFacts(), hasEntry("tokenExpiresInSeconds", (Object) 3599));
		assertThat(missingPermissions(response), is(List.of("Patrons_Hold_Request_Create")));
	}

	@Test
	void shouldReportNothingMissingWhenTheKeyHasEveryPermissionDcbUses() {
		tokenInfoGrants(new ArrayList<>(SierraLmsClient.PERMISSIONS_DCB_USES));

		final var response = ping();

		assertThat(missingPermissions(response), is(List.of()));
		assertThat(grantedPermissions(response), hasItem("Patrons_Hold_Request_Create"));
		assertThat(grantedPermissions(response), not(hasItem("Fines_List")));
	}

	private void tokenInfoGrants(List<String> permissions) {
		mockServerClient.when(request().withMethod("GET").withPath("/iii/sierra-api/v6/info/token"))
			.respond(okJson(Map.of(
				"keyId", "key",
				"grantType", "client_credentials",
				"authorizationScheme", "client",
				"expiresIn", 3599,
				"roles", List.of(Map.of("name", "dcb", "tokenLifetime", 3600, "permissions", permissions)))));
	}

	@SuppressWarnings("unchecked")
	private static List<String> missingPermissions(PingResponse response) {
		return (List<String>) response.getFacts().get("missingPermissions");
	}

	@SuppressWarnings("unchecked")
	private static List<String> grantedPermissions(PingResponse response) {
		return (List<String>) response.getFacts().get("grantedPermissions");
	}

	private PingResponse ping() {
		return hostLmsFixture.createClient(HOST_LMS_CODE).ping().block();
	}
}

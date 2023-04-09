package org.olf.reshare.dcb.core.interaction.sierra;

import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.Item;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(transactional = false, propertySources = { "classpath:configs/SierraLmsClientTests.yml" }, rebuildContext = true)
@TestInstance(Lifecycle.PER_CLASS)
class SierraLmsClientTests {
	private static final String SIERRA_TOKEN = "test-token-for-user";

	// Properties should line up with included property source for the spec.
	@Property(name = "hosts.test1.client.base-url")
	private String sierraHost;

	@Property(name = "hosts.test1.client.key")
	private String sierraUser;

	@Property(name = "hosts.test1.client.secret")
	private String sierraPass;

	@Inject
	private ResourceLoader loader;

	@Inject
	private HostLmsService hostLmsService;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		SierraTestUtils.mockFor(mock, sierraHost)
			.setValidCredentials(sierraUser, sierraPass, SIERRA_TOKEN, 60);

		sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);
	}

	@Test
	void shouldProvideMultipleItemsWhenSierraRespondsWithMultipleItems() {
		sierraItemsAPIFixture.threeItemsResponseForBibId("4564554664");

		final var client = hostLmsService.getClientFor("test1").block();

		final var items = getItemsByBibId(client, "4564554664", "hostLmsCode");

		assertThat(items, is(notNullValue()));
		assertThat(items, hasSize(3));

		final var firstItem = items.get(0);

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem.getId(), is("f2010365-e1b1-4a5d-b431-a3c65b5f23fb"));

		final var secondItem = items.get(1);

		assertThat(secondItem, is(notNullValue()));
		assertThat(secondItem.getId(), is("c5bc9cd0-fc23-48be-9d52-647cea8c63ca"));

		final var thirdItem = items.get(2);

		assertThat(thirdItem, is(notNullValue()));
		assertThat(thirdItem.getId(), is("69415d0a-ace5-49e4-96fd-f63855235bf0"));
	}

	@Test
	void shouldProvideNoItemsWhenSierraRespondsWithNoRecordsFoundError() {
		sierraItemsAPIFixture.zeroItemsResponseForBibId("65423515");

		final var client = hostLmsService.getClientFor("test1").block();

		final var items = getItemsByBibId(client, "65423515", "HostLmsCode");

		assertThat(items, hasSize(0));
	}

	@Test
	void shouldReportErrorWhenSierraRespondsWithInternalServerError() {
		sierraItemsAPIFixture.serverErrorResponseForBibId("565496");

		final var client = hostLmsService.getClientFor("test1").block();

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> getItemsByBibId(client, "565496", "hostLmsCode"));

		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(INTERNAL_SERVER_ERROR));
		assertThat(response.code(), is(500));

		final var optionalBody = response.getBody(String.class);

		assertThat(optionalBody.isPresent(), is(true));

		final var body = optionalBody.get();

		assertThat(body, is("Broken"));
	}

	private static List<Item> getItemsByBibId(HostLmsClient client, String bibId,
		String hostLmsCode) {

		return client.getItemsByBibId(bibId, hostLmsCode).block();
	}
}

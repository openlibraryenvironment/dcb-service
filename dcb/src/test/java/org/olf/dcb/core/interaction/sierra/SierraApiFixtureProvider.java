package org.olf.dcb.core.interaction.sierra;

import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.MockServer;
import org.olf.dcb.test.MockServerCommonRequests;
import org.olf.dcb.test.TestResourceLoader;
import org.olf.dcb.test.TestResourceLoaderProvider;

import jakarta.inject.Singleton;

@Singleton
public class SierraApiFixtureProvider {
	private final TestResourceLoader resourceLoader;

	public SierraApiFixtureProvider(TestResourceLoaderProvider testResourceLoaderProvider) {
		this.resourceLoader = testResourceLoaderProvider.forBasePath("classpath:mock-responses/sierra/");
	}

	public SierraLoginAPIFixture login(MockServerClient mockServerClient, String host) {
		return new SierraLoginAPIFixture(createMockServer(mockServerClient, host), new MockServerCommonRequests(host));
	}

	SierraLoginAPIFixture login(MockServerClient mockServerClient) {
		return login(mockServerClient, null);
	}

	public SierraPickupLocationsAPIFixture pickupLocations(MockServerClient mockServerClient, String host) {
		return new SierraPickupLocationsAPIFixture(createMockServer(mockServerClient, host));
	}

	public SierraItemsAPIFixture items(MockServerClient mockServerClient, String host) {
		return new SierraItemsAPIFixture(createMockServer(mockServerClient, host));
	}

	public SierraItemsAPIFixture items(MockServerClient mockServerClient) {
		return items(mockServerClient, null);
	}

	public SierraBibsAPIFixture bibs(MockServerClient mockServerClient, String host) {
		return new SierraBibsAPIFixture(createMockServer(mockServerClient, host), new MockServerCommonRequests(host));
	}

	public SierraBibsAPIFixture bibs(MockServerClient mockServerClient) {
		return bibs(mockServerClient, null);
	}

	public SierraPatronsAPIFixture patrons(MockServerClient mockServerClient, String host) {
		return new SierraPatronsAPIFixture(createMockServer(mockServerClient, host));
	}

	public SierraPatronsAPIFixture patrons(MockServerClient mockServerClient) {
		return patrons(mockServerClient, null);
	}

	private MockServer createMockServer(MockServerClient mockServerClient, String host) {
		return new MockServer(mockServerClient, new MockServerCommonRequests(host), resourceLoader);
	}
}

package org.olf.dcb.core.interaction.sierra;

import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.TestResourceLoaderProvider;

import jakarta.inject.Singleton;

@Singleton
public class SierraApiFixtureProvider {
	private final TestResourceLoaderProvider testResourceLoaderProvider;

	public SierraApiFixtureProvider(TestResourceLoaderProvider testResourceLoaderProvider) {
		this.testResourceLoaderProvider = testResourceLoaderProvider;
	}

	public SierraLoginAPIFixture loginFixtureFor(MockServerClient mockServerClient) {
		return new SierraLoginAPIFixture(mockServerClient, testResourceLoaderProvider);
	}
}

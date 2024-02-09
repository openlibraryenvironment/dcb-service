package org.olf.dcb.core.interaction.polaris;

import static org.mockserver.model.HttpResponse.response;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;

import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.TestResourceLoader;

import services.k_int.interaction.polaris.PolarisTestUtils;

public class MockPolarisFixture {
	private final MockServerClient mockServerClient;
	private final TestResourceLoader resourceLoader;
	private final PolarisTestUtils.MockPolarisPAPIHost mockPolaris;

	public MockPolarisFixture(MockServerClient mockServerClient,
		TestResourceLoader resourceLoader,
		PolarisTestUtils.MockPolarisPAPIHost mockPolaris) {

		this.mockServerClient = mockServerClient;
		this.resourceLoader = resourceLoader;
		this.mockPolaris = mockPolaris;
	}

	void mock(String method, String path, String jsonResource) {
		mockPolaris.whenRequest(req -> req
				.withMethod(method)
				.withPath(path))
			.respond(okJson(resourceLoader.getResource(jsonResource)));
	}

	void mock(String method, String path, Integer statusCode, String body) {
		mockPolaris.whenRequest(req -> req
				.withMethod(method)
				.withPath(path))
			.respond(response().withStatusCode(statusCode).withBody(body));
	}
}

package org.olf.dcb.core.interaction.sierra;

import static org.mockserver.model.JsonBody.json;

import org.mockserver.model.HttpRequest;
import org.olf.dcb.test.MockServerCommonRequests;

public class SierraMockServerRequests {
	private final String basePath;
	private final MockServerCommonRequests mockServerCommonRequests;

	public SierraMockServerRequests(String basePath) {
		this.basePath = basePath;
		mockServerCommonRequests = new MockServerCommonRequests("", null);
	}

	public HttpRequest post() {
		return post("");
	}

	HttpRequest post(String subPath) {
		return mockServerCommonRequests.post(basePath + subPath);
	}

	HttpRequest post(Object body) {
		return post("", body);
	}

	HttpRequest post(String subPath, Object body) {
		return post(subPath).withBody(json(body));
	}

	HttpRequest get() {
		return get("");
	}

	HttpRequest get(String subPath) {
		return mockServerCommonRequests.get(basePath + subPath);
	}

	HttpRequest delete(String subPath) {
		return mockServerCommonRequests.delete(basePath + subPath);
	}

	HttpRequest put(String subPath) {
		return mockServerCommonRequests.put(basePath + subPath);
	}
}

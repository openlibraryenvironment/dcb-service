package org.olf.dcb.core.interaction.sierra;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;

public class SierraMockServerRequests {
	private final String basePath;

	public SierraMockServerRequests(String basePath) {
		this.basePath = basePath;
	}

	public HttpRequest post() {
		return post("");
	}

	HttpRequest post(String subPath) {
		return acceptsJson("POST", subPath);
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
		return acceptsJson("GET", subPath);
	}

	public RequestDefinition put(String subPath) {
		return acceptsJson("PUT", subPath);
	}

	private HttpRequest acceptsJson(String method, String subPath) {
		return request()
			.withHeader("Accept", APPLICATION_JSON.toString())
			.withMethod(method)
			.withPath(basePath + subPath);
	}
}

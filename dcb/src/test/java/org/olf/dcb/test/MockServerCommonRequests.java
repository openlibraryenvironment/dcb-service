package org.olf.dcb.test;

import static org.mockserver.model.HttpRequest.request;

import org.mockserver.model.HttpRequest;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MockServerCommonRequests {
	private final String host;
	private final String apiKey;

	public MockServerCommonRequests(String host) {
		this(host, null);
	}

	public HttpRequest get(String path) {
		return baselineRequest("GET", path);
	}

	public HttpRequest get(String path, String queryName, String queryValue) {
		return get(path).withQueryStringParameter(queryName, queryValue);
	}

	public HttpRequest post(String path) {
		return baselineRequest("POST", path);
	}

	public HttpRequest put(String path) {
		return baselineRequest("PUT", path);
	}

	public HttpRequest delete(String path) {
		return baselineRequest("DELETE", path);
	}

	private HttpRequest baselineRequest(String method, String path) {
		var request = request().withHeader("Accept", "application/json");

		// Some tests share the same mockserver expectations between multiple Host LMS that have different hosts
		if (host != null) {
			request.withHeader("Host", host);
		}

		// Not all systems use an authorization header
		if (apiKey != null) {
			request.withHeader("Authorization", apiKey);
		}

		return request
			.withMethod(method)
			.withPath(path);
	}
}

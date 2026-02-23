package org.olf.dcb.test;

import static org.mockserver.model.JsonBody.json;
import static org.mockserver.verify.VerificationTimes.never;
import static org.mockserver.verify.VerificationTimes.once;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class MockServer {
	private final MockServerClient client;
	private final MockServerCommonRequests commonRequests;
	private final TestResourceLoader resourceLoader;

	public MockServer(MockServerClient client, MockServerCommonRequests commonRequests) {
		this(client, commonRequests, null);
	}

	public void mockGet(String path, HttpResponse response) {
		mock(commonRequests.get(path), response);
	}

	public void mockGet(String path, Object responseBody) {
		mock(commonRequests.get(path), okJson(responseBody));
	}

	public void mockGet(String path, String queryName, String queryValue, HttpResponse response) {
		mock(commonRequests.get(path, queryName, queryValue), response);
	}

	public void mockGet(String path, String jsonResourcePath) {
		mock(commonRequests.get(path), okJson(getResource(jsonResourcePath)));
	}

	public void mockPost(String path, HttpResponse response) {
		mock(commonRequests.post(path), response);
	}

	public void mockPost(String path, Object requestBody, Object responseBody) {
		mock(commonRequests.post(path).withBody(json(requestBody)), okJson(responseBody));
	}

	public void mockPost(String path, String jsonResourcePath) {
		mock(commonRequests.post(path), okJson(getResource(jsonResourcePath)));
	}

	public void mockPut(String path, Object responseBody) {
		mock(commonRequests.put(path), okJson(responseBody));
	}

	public void mockPut(String path, HttpResponse response) {
		mock(commonRequests.put(path), response);
	}

	public void mock(HttpRequest request, HttpResponse response) {
		log.trace("Establishing mock for: {}", request.getPath());

		mock(request, response, Times.unlimited());
	}

	public void mock(HttpRequest request, Object responseBody) {
		mock(request, okJson(responseBody));
	}

	public void mock(HttpRequest request, String jsonResourcePath) {
		mock(request, okJson(getResource(jsonResourcePath)));
	}

	public void mock(HttpRequest request, Object responseBody, Times times) {
		mock(request, okJson(responseBody), times);
	}

	public void mock(HttpRequest request, HttpResponse response, Times times) {
		client.when(request, times).respond(response);
	}

	public void mock(HttpRequest request, String jsonResourcePath, Times times) {
		mock(request, okJson(getResource(jsonResourcePath)), times);
	}

	public void replaceMock(HttpRequest request, HttpResponse response, Times times) {
		// Remove previous expectations, sometimes used when there is no way to match request more specifically
		client.clear(request);

		mock(request, response, times);
	}

	public void replaceMock(HttpRequest request, HttpResponse response) {
		// Remove previous expectations, sometimes used when there is no way to match request more specifically
		client.clear(request);

		mock(request, response);
	}

	public void replaceMock(HttpRequest request, Object responseBody) {
		replaceMock(request, okJson(responseBody));
	}

	public void replaceMock(HttpRequest request, String jsonResourcePath) {
		replaceMock(request, getResource(jsonResourcePath));
	}

	public void verify(HttpRequest expectedRequest) {
		verify(expectedRequest, once());
	}

	public void verify(HttpRequest expectedRequest, VerificationTimes times) {
		client.verify(expectedRequest, times);
	}

	public void verifyPost(String path, Object expectedBody) {
		verify(commonRequests.post(path).withBody(json(expectedBody)));
	}

	public void verifyPut(String path, Object expectedBody) {
		verify(commonRequests.put(path).withBody(json(expectedBody)));
	}

	public void verifyPut(String path) {
		verify(commonRequests.put(path));
	}

	public void verifyNever(HttpRequest request) {
		verify(request, never());
	}

	public void verifyPutNever(String path) {
		verifyNever(commonRequests.put(path));
	}

	private Object getResource(String jsonResourcePath) {
		return resourceLoader.getResource(jsonResourcePath);
	}
}

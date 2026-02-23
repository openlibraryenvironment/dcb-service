package org.olf.dcb.test;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import org.mockserver.model.HttpResponse;

public class MockServerCommonResponses {
	public static HttpResponse ok() {
		return response().withStatusCode(200);
	}

	public static HttpResponse okJson(Object body) {
		return ok().withBody(json(body, APPLICATION_JSON));
	}

	public static HttpResponse okText(String body) {
		return ok().withBody(body);
	}

	public static HttpResponse created(Object response) {
		return response().withStatusCode(201).withBody(json(response));
	}

	public static HttpResponse noContent() {
		return response().withStatusCode(204);
	}

	public static HttpResponse serverError() {
		return response().withStatusCode(500).withBody("Something went wrong");
	}

	public static HttpResponse serverError(Object body) {
		return response().withStatusCode(500).withBody(json(body, APPLICATION_JSON));
	}

	public static HttpResponse badRequest(Object body) {
		return response().withStatusCode(400).withBody(json(body));
	}

	public static HttpResponse notFound(Object body) {
		return notFoundResponse().withBody(json(body));
	}

	public static HttpResponse unauthorised() {
		return response().withStatusCode(401);
	}
}

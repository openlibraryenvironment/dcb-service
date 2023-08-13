package org.olf.dcb.core.interaction.sierra;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.BAD_REQUEST_400;
import static org.mockserver.model.HttpStatusCode.INTERNAL_SERVER_ERROR_500;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import java.io.InputStream;

import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;

public class SierraMockServerResponses {
	private final String basePath;
	private final ResourceLoader loader;

	public SierraMockServerResponses(String basePath, ResourceLoader loader) {
		this.basePath = basePath;
		this.loader = loader;
	}

	public HttpResponse jsonSuccess(String responseBodySubPath) {
		return jsonResponse(response(), responseBodySubPath);
	}

	public HttpResponse jsonSuccess(JsonBody body) {
		return jsonResponse(response(), body);
	}

	HttpResponse jsonLink(String link) {
		final var linkResult = LinkResult.builder()
			.link(link)
			.build();

		return jsonResponse(response(), json(linkResult));
	}

	HttpResponse noContent() {
		return response().withStatusCode(204);
	}

	HttpResponse noRecordsFound() {
		return jsonResponse(notFoundResponse(),
			json(Error.builder()
				.code(107)
				.specificCode(0)
				.httpStatus(404)
				.name("Record not found")
				.build()));
	}

	HttpResponse badRequestError() {
		return jsonResponse(response().withStatusCode(BAD_REQUEST_400.code()),
			json(Error.builder()
				.code(130)
				.specificCode(0)
				.httpStatus(400)
				.name("Bad JSON/XML Syntax")
				.description("Please check that the JSON fields/values are of the expected JSON data types")
				.build()));
	}

	public HttpResponse serverError() {
		return jsonResponse(response().withStatusCode(INTERNAL_SERVER_ERROR_500.code()),
			json(Error.builder()
				.code(109)
				.specificCode(0)
				.httpStatus(500)
				.name("Internal server error")
				.description("Invalid configuration")
				.build()));
	}

	public HttpResponse unauthorised() {
		return response().withStatusCode(401);
	}

	private HttpResponse jsonResponse(HttpResponse response, String responseBodySubPath) {
		return jsonResponse(response, getJsonResource(responseBodySubPath));
	}

	private JsonBody getJsonResource(String subPath) {
		return json(getResource(subPath));
	}

	private String getResource(String responseBodySubPath) {
		return getResourceAsString(basePath + responseBodySubPath);
	}

	private String getResourceAsString(String resourcePath) {
		return loader.getResourceAsStream(resourcePath)
			.map(SierraMockServerResponses::resourceToString)
			.orElseThrow(() -> new RuntimeException("Resource could not be found: " + resourcePath));
	}

	@SneakyThrows
	private static String resourceToString(InputStream resource) {
		return new String(resource.readAllBytes());
	}

	private HttpResponse jsonResponse(HttpResponse response, JsonBody body) {
		return response
			.withContentType(APPLICATION_JSON)
			.withBody(body);
	}

	@Serdeable
	@Data
	@Builder
	private static class LinkResult {
		String link;
	}

	@Serdeable
	@Data
	@Builder
	private static class Error {
		Integer code;
		Integer specificCode;
		Integer httpStatus;
		String name;
		String description;
	}
}

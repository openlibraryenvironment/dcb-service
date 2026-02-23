package org.olf.dcb.core.interaction.sierra;

import static org.olf.dcb.test.MockServerCommonResponses.badRequest;
import static org.olf.dcb.test.MockServerCommonResponses.notFound;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;

import org.mockserver.model.HttpResponse;
import org.olf.dcb.test.MockServerCommonResponses;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import services.k_int.interaction.sierra.LinkResult;

class SierraMockServerResponses {
	static HttpResponse jsonLink(String link) {
		final var linkResult = LinkResult.builder()
			.link(link)
			.build();

		return okJson(linkResult);
	}

	static HttpResponse noRecordsFound() {
		return notFound(Error.builder()
			.code(107)
			.specificCode(0)
			.httpStatus(404)
			.name("Record not found")
			.build());
	}

	static HttpResponse thisRecordIsNotAvailable() {
		return MockServerCommonResponses.serverError((Error.builder()
				.code(132)
				.specificCode(2)
				.httpStatus(500)
				.name("XCirc error")
				.description("This record is not available")
				.build()));
	}

	static HttpResponse badRequestError() {
		return badRequest(Error.builder()
			.code(130)
			.specificCode(0)
			.httpStatus(400)
			.name("Bad JSON/XML Syntax")
			.description("Please check that the JSON fields/values are of the expected JSON data types")
			.build());
	}

	static HttpResponse serverError() {
		return MockServerCommonResponses.serverError(Error.builder()
			.code(109)
			.specificCode(0)
			.httpStatus(500)
			.name("Internal server error")
			.description("Invalid configuration")
			.build());
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

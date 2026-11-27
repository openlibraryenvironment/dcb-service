package org.olf.dcb.preferences;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

/**
 * A refused preference request as problem+json with its reason: the framework default drops the
 * detail. Nothing is logged, because a message can quote what the caller tried to store.
 */
@Singleton
public class PreferenceExceptionHandler
	implements ExceptionHandler<PreferenceException, HttpResponse<PreferenceExceptionHandler.Problem>> {

	private static final MediaType PROBLEM_JSON = MediaType.of("application/problem+json");

	@Serdeable
	public record Problem(String type, String title, int status, String detail, String code) {
	}

	@Override
	public HttpResponse<Problem> handle(HttpRequest request, PreferenceException exception) {
		return HttpResponse.status(exception.status())
			.contentType(PROBLEM_JSON)
			.body(new Problem(
				"urn:openrs:dcb-preferences:" + exception.code().toLowerCase(),
				"Preference request refused",
				exception.status().getCode(),
				exception.getMessage(),
				exception.code()));
	}
}

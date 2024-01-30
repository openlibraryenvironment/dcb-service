package org.olf.dcb.core.interaction;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.stream.Collectors;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;

public class HttpProtocolToLogMessageMapper {

	public static String toLogOutput(HttpResponse<?> response) {
		if (response == null) {
			return "No response included in error";
		}

		return "Status: \"%s\"\nHeaders: %s\nBody: %s\n".formatted(
			getValue(response, HttpResponse::getStatus),
			toLogOutput(response.getHeaders()),
			response.getBody(Argument.of(String.class))
		);
	}

	private static String toLogOutput(HttpHeaders headers) {
		return headers.asMap().entrySet().stream()
			.map(entry -> "%s: %s".formatted(entry.getKey(), entry.getValue()))
			.collect(Collectors.joining("; "));
	}

	public static <T> String toLogOutput(MutableHttpRequest<T> request) {
		if (request == null) {
			return "Request is null";
		}

		return request + " with body: \"" + request.getBody(Argument.of(String.class)) + "\"";
	}
}

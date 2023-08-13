package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static io.micronaut.http.HttpStatus.UNAUTHORIZED;

import java.util.function.Predicate;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import services.k_int.interaction.sierra.SierraError;

class SierraResponseErrorMatcher {
	boolean isNoRecordsError(Throwable throwable) {
		return isClientResponseException(throwable,
			isStatus(NOT_FOUND).and(isNoRecordsFoundError()));
	}

	boolean isUnauthorised(Throwable throwable) {
		return isClientResponseException(throwable, isStatus(UNAUTHORIZED));
	}

	private Predicate<HttpResponse<?>> isNoRecordsFoundError() {
		return this::isNoRecordsFoundError;
	}

	private boolean isNoRecordsFoundError(HttpResponse<?> response) {
		final var optionalBody = response.getBody(SierraError.class);

		if (optionalBody.isEmpty()) {
			return false;
		}

		final var body = optionalBody.get();

		return body.getCode() == 107;
	}

	private static Predicate<HttpResponse<?>> isStatus(HttpStatus status) {
		return r -> r.getStatus() == status;
	}

	private static boolean isClientResponseException(Throwable throwable,
		Predicate<HttpResponse<?>> responsePredicate) {

		if (throwable instanceof HttpClientResponseException exception) {
			return responsePredicate.test(exception.getResponse());
		}
		else {
			return false;
		}
	}
}

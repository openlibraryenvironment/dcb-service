package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static org.olf.dcb.core.interaction.HttpResponsePredicates.isClientResponseException;
import static org.olf.dcb.core.interaction.HttpResponsePredicates.isStatus;

import java.util.function.Predicate;

import io.micronaut.http.HttpResponse;
import services.k_int.interaction.sierra.SierraError;

class SierraResponseErrorMatcher {
	boolean isNoRecordsError(Throwable throwable) {
		return isClientResponseException(throwable, isStatus(NOT_FOUND).and(isNoRecordsFoundError()));
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
}

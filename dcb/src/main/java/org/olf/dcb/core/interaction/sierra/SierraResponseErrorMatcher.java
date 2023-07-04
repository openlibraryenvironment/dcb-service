package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.http.HttpStatus.NOT_FOUND;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import services.k_int.interaction.sierra.SierraError;

class SierraResponseErrorMatcher {
	private static final Logger log = LoggerFactory.getLogger(SierraResponseErrorMatcher.class);

	boolean isNoRecordsError(Throwable throwable) {
		log.debug("Attempting to match error from Sierra client");

		if (throwable instanceof HttpClientResponseException exception) {
			final var response = exception.getResponse();

			if (response.getStatus() != NOT_FOUND) {
				return false;
			} else {
				final var optionalBody = response.getBody(SierraError.class);

				if (optionalBody.isEmpty()) {
					return false;
				} else {
					final var body = optionalBody.get();

					return body.getCode() == 107;
				}
			}
		} else {
			return false;
		}
	}
}

package org.olf.dcb.core.interaction.sierra;

import io.micronaut.http.HttpResponse;
import services.k_int.interaction.sierra.SierraError;

import java.util.Optional;

import static org.olf.dcb.core.interaction.HttpResponsePredicates.isClientResponseException;

class SierraResponseErrorMatcher {
	/**
	 * Error codes as defined in the Sierra API documentation.
	 * These codes correspond to specific error conditions described in the API documentation.
	 *
	 * @see <a href="https://techdocs.iii.com/sierraapi/Content/zAppendix/errorHandling.htm">Sierra API Error Handling</a>
	 */
	private static final int RECORD_NOT_FOUND = 107;
	private static final int REQUEST_DENIED_BY_XCIRC = 132;

	public boolean isNoRecordsError(Throwable throwable) {
		return isClientResponseException(throwable, this::isNoRecordsFoundError);
	}

	public boolean isRecordNotAvailable(Throwable throwable) {
		return isClientResponseException(throwable, this::isRecordNotAvailable);
	}

	private boolean isNoRecordsFoundError(HttpResponse<?> response) {
		return hasErrorCode(response, RECORD_NOT_FOUND);
	}

	private boolean isRecordNotAvailable(HttpResponse<?> response) {
		return hasErrorCode(response, REQUEST_DENIED_BY_XCIRC);
	}

	private boolean hasErrorCode(HttpResponse<?> response, int errorCode) {
		return getErrorBody(response)
			.map(SierraError::getCode)
			.map(code -> code == errorCode)
			.orElse(false);
	}

	private Optional<SierraError> getErrorBody(HttpResponse<?> response) {
		return response.getBody(SierraError.class);
	}
}

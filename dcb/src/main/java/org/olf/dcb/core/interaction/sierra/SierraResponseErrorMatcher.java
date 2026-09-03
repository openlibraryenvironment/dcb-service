package org.olf.dcb.core.interaction.sierra;

import io.micronaut.http.HttpResponse;
import services.k_int.interaction.sierra.SierraError;

import java.util.Optional;
import java.util.function.Predicate;

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

	/**
	 * 132 is a category ("Request denied by XCirc"), not a diagnosis, and specificCode
	 * does not narrow it - 132/2 is reported for "This record is not available", for
	 * "Bib record cannot be loaded" and for the patron record problem below. Sierra's
	 * description text is the only discriminator it gives us.
	 * <p>
	 * Matching on prose is brittle. It is still preferable to the alternative of
	 * reporting every XCirc denial as an item availability problem and attaching item
	 * diagnostics to faults that lie with the patron record.
	 */
	private static final String PATRON_RECORD_PROBLEM = "problem with your library record";

	public boolean isNoRecordsError(Throwable throwable) {
		return isClientResponseException(throwable, this::isNoRecordsFoundError);
	}

	public boolean isPatronRecordProblem(Throwable throwable) {
		return isClientResponseException(throwable, this::isPatronRecordProblem);
	}

	public boolean isRecordNotAvailable(Throwable throwable) {
		return isClientResponseException(throwable, this::isRecordNotAvailable);
	}

	private boolean isNoRecordsFoundError(HttpResponse<?> response) {
		return hasErrorCode(response, RECORD_NOT_FOUND);
	}

	private boolean isPatronRecordProblem(HttpResponse<?> response) {
		return test(response, error -> error.getCode() == REQUEST_DENIED_BY_XCIRC
			&& describes(error, PATRON_RECORD_PROBLEM));
	}

	// Any XCirc denial we cannot attribute to the patron record keeps the existing
	// treatment, so that unrecognised descriptions still surface item state.
	private boolean isRecordNotAvailable(HttpResponse<?> response) {
		return hasErrorCode(response, REQUEST_DENIED_BY_XCIRC)
			&& !isPatronRecordProblem(response);
	}

	private static boolean describes(SierraError error, String text) {
		final var description = error.getDescription();

		return description != null && description.toLowerCase().contains(text);
	}

	private boolean hasErrorCode(HttpResponse<?> response, int errorCode) {
		return test(response, error -> error.getCode() == errorCode);
	}

	private boolean test(HttpResponse<?> response, Predicate<SierraError> predicate) {
		return getErrorBody(response)
			.map(predicate::test)
			.orElse(false);
	}

	private Optional<SierraError> getErrorBody(HttpResponse<?> response) {
		return response.getBody(SierraError.class);
	}
}

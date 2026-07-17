package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.http.HttpResponse.notFound;
import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.serverError;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import services.k_int.interaction.sierra.SierraError;

class SierraResponseErrorMatcherTests {
	private final SierraResponseErrorMatcher errorMatcher = new SierraResponseErrorMatcher();

	@Test
	void shouldNotBeNoRecordsWhenExceptionIsNotHttpClientResponseException() {
		assertThat(errorMatcher.isNoRecordsError(new RuntimeException()), is(false));
	}

	@Test
	void shouldNotBeNoRecordsFoundWhenStatusCodeIsAnythingExceptNotFound() {
		final var exception = new HttpClientResponseException("", ok());

		assertThat(errorMatcher.isNoRecordsError(exception), is(false));
	}

	@Test
	void shouldNotBeNoRecordsFoundWhenBodyIsNotSierraError() {
		final var exception = new HttpClientResponseException("",
			notFound("Some message"));

		assertThat(errorMatcher.isNoRecordsError(exception), is(false));
	}

	@Test
	void shouldNotBeNoRecordsFoundWhenCodeIsAnythingExcept107() {
		final var exception = new HttpClientResponseException("",
			notFound(createSierraError(345)));

		assertThat(errorMatcher.isNoRecordsError(exception), is(false));
	}

	@Test
	void shouldBeNoRecordsFoundWhenCodeIs107() {
		final var exception = new HttpClientResponseException("",
			notFound().body(createSierraError(107)));

		assertThat(errorMatcher.isNoRecordsError(exception), is(true));
	}

	// XCirc reports 132 / specificCode 2 for both of the conditions below. Only the
	// description tells them apart, so these two cases must stay mutually exclusive.

	@Test
	void shouldBePatronRecordProblemWhenXCircDescribesTheLibraryRecord() {
		final var exception = new HttpClientResponseException("",
			serverError().body(createXCircError(
				"XCirc error : There is a problem with your library record.  Please see a librarian.")));

		assertThat(errorMatcher.isPatronRecordProblem(exception), is(true));
		assertThat(errorMatcher.isRecordNotAvailable(exception), is(false));
	}

	@Test
	void shouldBeRecordNotAvailableWhenXCircDescribesTheRecord() {
		final var exception = new HttpClientResponseException("",
			serverError().body(createXCircError("This record is not available")));

		assertThat(errorMatcher.isRecordNotAvailable(exception), is(true));
		assertThat(errorMatcher.isPatronRecordProblem(exception), is(false));
	}

	@Test
	void shouldFallBackToRecordNotAvailableWhenXCircDescriptionIsUnrecognised() {
		final var exception = new HttpClientResponseException("",
			serverError().body(createXCircError("Bib record cannot be loaded")));

		assertThat(errorMatcher.isRecordNotAvailable(exception), is(true));
		assertThat(errorMatcher.isPatronRecordProblem(exception), is(false));
	}

	@Test
	void shouldFallBackToRecordNotAvailableWhenXCircGivesNoDescription() {
		final var exception = new HttpClientResponseException("",
			serverError().body(createXCircError(null)));

		assertThat(errorMatcher.isRecordNotAvailable(exception), is(true));
		assertThat(errorMatcher.isPatronRecordProblem(exception), is(false));
	}

	@Test
	void shouldNotBePatronRecordProblemWhenCodeIsAnythingExcept132() {
		final var exception = new HttpClientResponseException("",
			serverError().body(new SierraError("", 109, 2,
				"Internal server error", "There is a problem with your library record")));

		assertThat(errorMatcher.isPatronRecordProblem(exception), is(false));
	}

	private static SierraError createSierraError(int code) {
		return new SierraError("", code, 0, "", "");
	}

	private static SierraError createXCircError(String description) {
		return new SierraError("", 132, 2, "XCirc error", description);
	}
}

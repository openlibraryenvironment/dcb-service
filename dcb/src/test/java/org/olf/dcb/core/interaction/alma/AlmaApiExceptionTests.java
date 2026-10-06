package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.junit.jupiter.api.Test;

import services.k_int.interaction.alma.types.error.AlmaError;
import services.k_int.interaction.alma.types.error.AlmaErrorList;
import services.k_int.interaction.alma.types.error.AlmaErrorResponse;

class AlmaApiExceptionTests {
	@Test
	void shouldRecogniseACodeTheAdapterActsOn() {
		final var exception = new AlmaApiException("GET", "/almaws/v1/users/P1", 400, response("401861"));

		assertThat(exception.has(AlmaApiException.Code.USER_NOT_FOUND), is(true));
		assertThat(exception.has(AlmaApiException.Code.AUTHENTICATION_FAILED), is(false));
		assertThat(exception.getStatusCode(), is(400));
	}

	@Test
	void shouldKeepACodeItDoesNotNameWithoutMatchingIt() {
		final var exception = new AlmaApiException("GET", "/almaws/v1/users/P1", 400, response("999999"));

		assertThat(exception.getErrorCodes(), contains("999999"));

		for (AlmaApiException.Code code : AlmaApiException.Code.values()) {
			assertThat(exception.has(code), is(false));
		}
	}

	@Test
	void shouldCarryOnlyTheMethodPathAndAlmasResponseAsParameters() {
		final var exception = new AlmaApiException("GET", "/almaws/v1/users/P1", 429,
			response("PER_SECOND_THRESHOLD"));

		assertThat(exception.getParameters().keySet(),
			containsInAnyOrder("Request Method", "Request path", "Alma Error response"));
		assertThat(exception.has(AlmaApiException.Code.PER_SECOND_THRESHOLD), is(true));
	}

	private static AlmaErrorResponse response(String code) {
		final var error = new AlmaError();
		error.setErrorCode(code);
		error.setErrorMessage("any message");

		final var errorList = new AlmaErrorList();
		errorList.setError(List.of(error));

		final var response = new AlmaErrorResponse();
		response.setErrorsExist(true);
		response.setErrorList(errorList);

		return response;
	}
}

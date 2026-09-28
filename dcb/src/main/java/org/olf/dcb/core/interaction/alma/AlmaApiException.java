package org.olf.dcb.core.interaction.alma;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

import services.k_int.interaction.alma.types.error.AlmaError;
import services.k_int.interaction.alma.types.error.AlmaErrorResponse;

/**
 * An error response from the Alma API, with the Alma error codes it carried.
 */
public class AlmaApiException extends AbstractThrowableProblem {

	/** The Alma error codes the adapter acts on. */
	public enum Code {
		USER_NOT_FOUND("401861"),
		REQUEST_NOT_FOUND("401694"),
		NO_ITEM_CAN_FULFIL("401129"),
		NO_ITEM_FOR_BARCODE("401689"),
		AUTHENTICATION_FAILED("401866"),
		PER_SECOND_THRESHOLD("PER_SECOND_THRESHOLD"),
		DAILY_THRESHOLD("DAILY_THRESHOLD");

		private final String almaCode;

		Code(String almaCode) {
			this.almaCode = almaCode;
		}
	}

	private final int statusCode;
	private final List<String> errorCodes;

	AlmaApiException(String method, String path, int statusCode, AlmaErrorResponse errorResponse) {
		super(null, "Alma API Error", Status.valueOf(statusCode), method + " " + path, null, null,
			parameters(method, path, errorResponse));

		this.statusCode = statusCode;
		this.errorCodes = codesOf(errorResponse);
	}

	public boolean has(Code code) {
		return errorCodes.contains(code.almaCode);
	}

	public int getStatusCode() {
		return statusCode;
	}

	public List<String> getErrorCodes() {
		return errorCodes;
	}

	// Copied into patron request audit data, so it carries no request headers or bodies
	private static Map<String, Object> parameters(String method, String path, AlmaErrorResponse errorResponse) {
		return Map.of(
			"Request Method", method,
			"Request path", path,
			"Alma Error response", errorResponse);
	}

	private static List<String> codesOf(AlmaErrorResponse errorResponse) {
		if (errorResponse == null || errorResponse.getErrorList() == null
			|| errorResponse.getErrorList().getError() == null) {

			return List.of();
		}

		return errorResponse.getErrorList().getError().stream()
			.map(AlmaError::getErrorCode)
			.filter(Objects::nonNull)
			.toList();
	}
}

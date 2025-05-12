package services.k_int.interaction.alma.types.error;

import io.micronaut.http.HttpStatus;
import lombok.Getter;

@Getter
public class AlmaException extends RuntimeException {
	private final AlmaErrorResponse errorResponse;
	private final HttpStatus status;

	public AlmaException(String message, AlmaErrorResponse errorResponse, HttpStatus status) {
		super(message);
		this.errorResponse = errorResponse;
		this.status = status;
	}
}

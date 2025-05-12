package services.k_int.interaction.alma.types.error;

import java.util.Optional;

import io.micronaut.http.hateoas.JsonError;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;
import lombok.Setter;

@Serdeable
@Getter
@Setter
public class AlmaError extends JsonError {

	private String errorCode;
	private String errorMessage;
	private String trackingId;

	public AlmaError(String message) {
		super(message);
	}

	@Override
	public String getMessage() {
		StringBuilder builder = new StringBuilder();
		Optional.ofNullable(errorMessage).ifPresent(builder::append);
		Optional.ofNullable(errorCode).ifPresent(code -> builder.append(" [").append(code).append("]"));
		Optional.ofNullable(trackingId).ifPresent(id -> builder.append(" (trackingId: ").append(id).append(")"));
		return builder.toString();
	}

	@Override
	public String toString() {
		return getMessage();
	}
}

package services.k_int.interaction.alma.types.error;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;

@Serdeable.Deserializable
public class AlmaError extends JsonError {

	@Getter
	private String errorCode;

	@Getter
	private String errorMessage;

	public AlmaError() {
		this(null);
	}

	public AlmaError(@Nullable String message) {
		super(message);
	}

	public AlmaError(String message, String errorCode, String errorMessage) {
		super(message);
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}

	@JsonProperty("errorCode")
	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	@JsonProperty("errorMessage")
	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	@Override
	public String getMessage() {
		StringBuilder builder = new StringBuilder();

		Optional.ofNullable(errorCode).ifPresent(code -> builder.append("[").append(code).append("] "));
		Optional.ofNullable(errorMessage).ifPresent(msg -> builder.append(msg));

		return builder.toString();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();

		getLogref().ifPresent(logref -> builder.append('[').append(logref).append("] "));
		getPath().ifPresent(path -> builder.append(' ').append(path).append(" - "));
		Optional.ofNullable(getMessage()).ifPresent(builder::append);

		return builder.toString();
	}
}

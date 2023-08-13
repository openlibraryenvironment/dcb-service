package services.k_int.interaction.sierra;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;

@Serdeable.Deserializable
public class SierraError extends JsonError {
	public SierraError() {
		this(null);
	}

	@Getter
	private int code;

	@Getter
	private int specificCode;

	@Getter
	private String name;

	@Getter
	private String description;

	/**
	 * @param message The message
	 */
	public SierraError(@Nullable String message) {
		super(message);
	}

	public SierraError(String message, int code, int specificCode, String name,
		String description) {

		super(message);

		this.code = code;
		this.specificCode = specificCode;
		this.name = name;
		this.description = description;
	}

	@Override
	public String getMessage() {
		StringBuilder builder = new StringBuilder();
		builder.append(name);
		Optional.ofNullable(description).ifPresent(d -> builder.append(String.format(": %s", d)));
		Optional.ofNullable(code).ifPresent(c -> {
			builder.append(String.format(" - [%s", c));
			Optional.ofNullable(specificCode).ifPresent(sc ->
				builder.append(String.format(" / %s", sc))
			);
			builder.append("]");
		});

		return builder.toString();
	}

	@JsonProperty("code")
	public void setCode(int code) {
		this.code = code;
	}

	@JsonProperty("specificCode")
	public void setSpecificCode(int specificCode) {
		this.specificCode = specificCode;
	}

	@JsonProperty("name")
	public void setName(String name) {
		this.name = name;
	}

	@JsonProperty("description")
	public void setDescription(String description) {
		this.description = description;
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

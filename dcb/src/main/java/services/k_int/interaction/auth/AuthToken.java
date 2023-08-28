package services.k_int.interaction.auth;

import java.time.Duration;
import java.time.Instant;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AuthToken(
	@NotEmpty String value,
	@NotEmpty String type,
	@NotNull Instant expires
) {

	public static final Duration DEFAULT_EXPIRATION_BUFFER = Duration.ofSeconds(10);

	@JsonCreator
	public AuthToken(
		@JsonProperty("access_token")
		@NotEmpty String value,

		@JsonProperty("token_type")
		@NotEmpty String type,

		@JsonProperty("expires_in")
		@NotEmpty String expiry) {
		this(value, type, Instant.now().plus(Duration.parse("PT" + expiry + "S")).minus(DEFAULT_EXPIRATION_BUFFER));
	}

	public boolean isExpired() {
		return expires.isBefore(Instant.now());
	}

	@Override
	public String toString() {
		final String typeFormatted = type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();
		return String.format("%s %s", typeFormatted, this.value);
	}
}

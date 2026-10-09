package org.olf.dcb.core.interaction;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;

/** How far another system's clock is from ours, read from the Date header it answered with. */
public final class ClockSkew {
	public static final String FACT = "clockSkewSeconds";

	private ClockSkew() {
	}

	/** Seconds the other system's clock is ahead of ours, negative when it is behind. */
	public static Optional<Long> secondsFrom(HttpResponse<?> response) {
		return Optional.ofNullable(response.getHeaders().get(HttpHeaders.DATE))
			.flatMap(ClockSkew::parse)
			.map(theirs -> Duration.between(Instant.now(), theirs).getSeconds());
	}

	public static Map<String, Object> facts(HttpResponse<?> response) {
		return secondsFrom(response)
			.<Map<String, Object>>map(seconds -> Map.of(FACT, seconds))
			.orElse(Map.of());
	}

	static Optional<Instant> parse(String value) {
		try {
			return Optional.of(ZonedDateTime.parse(value, RFC_1123_DATE_TIME).toInstant());
		}
		catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}
}

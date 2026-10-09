package org.olf.dcb.core.interaction;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class PingResponse {
	public static final String OK = "OK";
	public static final String ERROR = "ERROR";
	/** The adapter has no ping: neither a success nor a failure. */
	public static final String NOT_IMPLEMENTED = "Not implemented";

	String target;
	String status;
	String additional;
	String versionInfo;
	Duration pingTime;
	/** One of {@link PingFailure}'s categories when the status is ERROR, otherwise null. */
	String failure;
	/** A few observations the adapter made while pinging, such as clock skew or remaining quota. */
	Map<String, Object> facts;
	Instant checkedAt;

	public static PingResponse ok(String target, String versionInfo, Duration pingTime) {
		return ok(target, versionInfo, pingTime, Map.of());
	}

	public static PingResponse ok(String target, String versionInfo, Duration pingTime,
		Map<String, Object> facts) {

		return new PingResponse(target, OK, null, versionInfo, pingTime, null, facts, Instant.now());
	}

	public static PingResponse error(String target, String versionInfo, String detail,
		Throwable cause, Duration pingTime) {

		return error(target, versionInfo, detail, PingFailure.classify(cause), pingTime, Map.of());
	}

	public static PingResponse error(String target, String versionInfo, String detail,
		String failure, Duration pingTime, Map<String, Object> facts) {

		return new PingResponse(target, ERROR, detail, versionInfo, pingTime, failure, facts, Instant.now());
	}

	/** DCB could not even build a client for the Host LMS, so its own configuration is at fault. */
	public static PingResponse misconfigured(String target, Throwable cause) {
		return error(target, null, cause.getMessage(), PingFailure.MISCONFIGURED, Duration.ZERO, Map.of());
	}

	public static PingResponse notImplemented(String target, String reason) {
		return new PingResponse(target, NOT_IMPLEMENTED, reason, null, Duration.ZERO, null, Map.of(),
			Instant.now());
	}

	/** One line for an operator: the status, the version the system reported, and any detail. */
	public String summary() {
		return "Status: " + status
			+ (versionInfo != null ? " (" + versionInfo + ")" : "")
			+ (additional != null || failure != null ? " - " : "")
			+ (failure != null ? failure + (additional != null ? ": " : "") : "")
			+ (additional != null ? additional : "");
	}
}

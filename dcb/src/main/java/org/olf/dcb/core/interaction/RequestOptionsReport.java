package org.olf.dcb.core.interaction;

import java.util.List;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * What a Host LMS says one patron may request on one copy, asked after a hold was refused.
 * <p>
 * Alma lists the request types it would accept but not where the item could be collected, and
 * no adapter reports pickup locations yet.
 * So {@code holdOffered} is null where the system did not say, and {@code pickupLocations}
 * is null where the system does not report them, which is not the same as an empty list.
 */
@Serdeable
public record RequestOptionsReport(
	String hostLmsCode,
	Status status,
	@Nullable String detail,
	@Nullable Boolean holdOffered,
	List<String> requestTypes,
	@Nullable List<ConfigurationReport.Entry> pickupLocations) {

	public enum Status {
		/** The Host LMS was asked, and the fields below are its answer. */
		CHECKED,
		/** This adapter cannot ask its Host LMS what a patron may request. */
		NOT_SUPPORTED,
		/** The adapter tried, and the Host LMS could not be asked. */
		FAILED
	}

	public RequestOptionsReport {
		requestTypes = requestTypes != null ? requestTypes : List.of();
	}

	public static RequestOptionsReport notSupported(String hostLmsCode, String detail) {
		return new RequestOptionsReport(hostLmsCode, Status.NOT_SUPPORTED, detail, null, List.of(), null);
	}

	public static RequestOptionsReport failed(String hostLmsCode, String detail) {
		return new RequestOptionsReport(hostLmsCode, Status.FAILED, detail, null, List.of(), null);
	}

	public boolean offersHold() {
		return status == Status.CHECKED && Boolean.TRUE.equals(holdOffered);
	}

	public boolean refusesHold() {
		return status == Status.CHECKED && Boolean.FALSE.equals(holdOffered);
	}
}

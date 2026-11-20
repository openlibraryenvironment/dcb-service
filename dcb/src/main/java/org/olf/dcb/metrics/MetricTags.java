package org.olf.dcb.metrics;

import java.util.Objects;
import java.util.Optional;

import org.olf.dcb.core.model.HostLms;

import io.micrometer.core.instrument.Tags;
import io.micronaut.core.annotation.Nullable;

public interface MetricTags {

	public static final String TAG_STATUS = "status";
	public static final String TAG_HOST = "host";
	public static final String TAG_EXCEPTION = "exception";

	public static final String TAG_VALUE_NONE = "none";
	public static final String TAG_VALUE_SUCCESS = "success";
	public static final String TAG_VALUE_ERROR = "error";

	public static Tags forHostLmsAndStatus(@Nullable HostLms lms, @Nullable MetricStatus status) {
		return hostLms(lms)
			.and(status(status));
	}

	public static Tags hostLms(@Nullable HostLms lms) {
		final String hostValue = Optional.ofNullable(lms)
				.map(HostLms::getCode).orElse(TAG_VALUE_NONE);
		return Tags.of(TAG_HOST, hostValue);
	}

	public static Tags status(@Nullable MetricStatus status) {

		final String statusValue = Objects.toString(status, TAG_VALUE_NONE);
		return Tags.of(TAG_STATUS, statusValue);
	}

}

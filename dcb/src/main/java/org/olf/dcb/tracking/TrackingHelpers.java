package org.olf.dcb.tracking;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest.Status;
import jakarta.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class TrackingHelpers {

	private final PollingConfig pollingConfig;

	public TrackingHelpers(PollingConfig pollingConfig) {
		this.pollingConfig = pollingConfig;
	}

	public Optional<Duration> getDurationFor(Status prStatus) {
		return Optional.ofNullable(pollingConfig.getDurations().get(prStatus));
	}

	public Map<Status, Duration> getDurations() {
		return pollingConfig.getDurations();
	}

}

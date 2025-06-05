package org.olf.dcb.tracking;

import reactor.core.publisher.Mono;
import java.time.Duration;

import java.util.UUID;

public interface TrackingService extends Runnable {

  public static final String LOCK_NAME = "tracking-service";
	public Mono<UUID> forceUpdate(UUID id);

  public default Duration getLastTrackingRunDuration() {
    return Duration.ZERO;
  }

  public default Long getLastTrackingRunCount() {
    return Long.valueOf(0);
  }
}


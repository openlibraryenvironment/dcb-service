package org.olf.dcb.tracking;

import org.olf.dcb.core.model.PatronRequest;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TrackingService extends Runnable {

  public static final String LOCK_NAME = "tracking-service";

	public Mono<PatronRequest> forceUpdate(UUID id);
}


package org.olf.dcb.core.svc;


import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.core.model.Alarm;

@Slf4j
@Singleton
public class AlarmsService {


	// 
	public Mono<Alarm> raise(Alarm alarm) {
		return Mono.just(alarm);
	}	

	// Prune expired alarms
	public void prune() {
		  // private Instant expires;
	}
}

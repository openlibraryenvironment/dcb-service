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

import org.olf.dcb.storage.AlarmRepository;

@Slf4j
@Singleton
public class AlarmsService {

	private final AlarmRepository alarmRepository;

	public AlarmsService(AlarmRepository alarmRepository) {
		this.alarmRepository = alarmRepository;
	}

	// 
	public Mono<Alarm> raise(Alarm alarm) {

		return Mono.from(alarmRepository.findById(alarm.getId()))
			.cast(Alarm.class)
			.flatMap(existingAlarm -> {
				// Update last seen on existing alarm
				existingAlarm.setLastSeen(Instant.now());
				existingAlarm.setExpires(alarm.getExpires());
				existingAlarm.incrementRepeatCount();
				return Mono.from(alarmRepository.update(existingAlarm)).cast(Alarm.class);
			})
			.switchIfEmpty( createNewAlarm(alarm) );
	}	

	private Mono<Alarm> createNewAlarm(Alarm alarm) {
		alarm.setLastSeen(Instant.now());
		alarm.setCreated(Instant.now());
		alarm.setRepeatCount(Long.valueOf(0));
		return Mono.from(alarmRepository.save(alarm));
	}

	// Prune expired alarms
	public void prune() {
    Instant now = Instant.now();

    Flux.from(alarmRepository.findByExpiresBefore(now))
        .flatMap(alarm -> alarmRepository.delete(alarm.getId()))
        .subscribe();
	}
}

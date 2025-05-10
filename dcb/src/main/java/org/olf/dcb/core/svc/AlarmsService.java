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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.URL;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.NoHostException;

import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.core.model.Alarm;

import org.olf.dcb.storage.AlarmRepository;
import reactor.util.function.Tuples;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Value;

@Slf4j
@Singleton
public class AlarmsService {

	private final AlarmRepository alarmRepository;

	// Default to an empty list
	// Add webhooks in environment variables as
	// DCB_GLOBAL_NOTIFICATIONS_WEBHOOKS[0]=https://example.com/notify
	@Value("${dcb.global.notifications.webhooks:}")
	List<String> webhookUrls;

	public AlarmsService(
		AlarmRepository alarmRepository
	){
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
			.switchIfEmpty( 
				createNewAlarm(alarm) 
					.flatMap( dbalarm -> 
						optionallyNotify(dbalarm.getCode(), "ACTIVATED")
							.map(result -> Tuples.of(dbalarm, result))
					)
					.map( tuple -> tuple.getT1() )
			);
	}	

	private Mono<Alarm> createNewAlarm(Alarm alarm) {
		alarm.setLastSeen(Instant.now());
		alarm.setCreated(Instant.now());
		alarm.setRepeatCount(Long.valueOf(0));
		return Mono.from(alarmRepository.save(alarm));

	}

	private Mono<String> optionallyNotify(String alarmCode, String status) {
		// Work out the context of the alarm, and see if that context has 
		// email or slack notifications configured, if so send
		log.info("Optionally notify {} : {}",alarmCode,status);
		List<String> combined = this.webhookUrls; // + any hostLms specific ones

		return Flux.fromIterable(combined)
			.map( target -> {
				log.info("Publish {} {} to {}",alarmCode, status, target);
				
				return publishToWebhook(target, Map.of("blocks", List.of(
					Map.of(
						"type", "section",
						"text", Map.of(
							"type", "markdown",
							"text", "DCB-ALARM: "+alarmCode+" "+status
						)
					)
				)));
			})
			.then(Mono.just("OK") );
	}

	// Prune expired alarms
	public void prune() {
    Instant now = Instant.now();

    Flux.from(alarmRepository.findByExpiresBefore(now))
				.flatMap(alarm -> 
					optionallyNotify(alarm.getCode(), "DEACTIVATED")
						.map(code -> alarmRepository.delete(alarm.getId()))
				)
        .subscribe();
	}

	public Mono<String> cancel(String code) {
		return Mono.from(alarmRepository.deleteByCode(code))
			.then( optionallyNotify(code, "DEACTIVATED") )
			.thenReturn( code );
	}


	public Mono<Void> publishToWebhook(String url, Map<String, Object> payload) {
		// This is where you get HttpClient programmatically
		try {
			log.info("Announce alarm on webhook : {}",url);

			HttpClient client = HttpClient.create(new URL(url));

			HttpRequest<Map<String, Object>> request = HttpRequest
				.POST(url, payload)
				.contentType(MediaType.APPLICATION_JSON_TYPE);
                
			// Sending the POST request to each webhook URL
			return Mono.from(client.exchange(request))
				.onErrorResume(NoHostException.class, e -> {
					// Handle the error gracefully, log or return fallback
					log.warn("Host could not be resolved: {} {}", e.getMessage(), url.toString());
					return Mono.empty(); // or a fallback Mono.just(...)
				})
				.then();
		}
		catch ( Exception e ) {
			return Mono.empty();
		}
	}

}

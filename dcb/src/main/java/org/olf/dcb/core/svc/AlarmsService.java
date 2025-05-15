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
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
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

import org.olf.dcb.configuration.NotificationEndpointDefinition;

@Slf4j
@Singleton
public class AlarmsService {

	private final AlarmRepository alarmRepository;

	// Default to an empty list
	// Add webhooks in environment variables as
	// DCB_GLOBAL_NOTIFICATIONS_WEBHOOKS[0]=https://example.com/notify
	List<NotificationEndpointDefinition> notificationEndpointDefinitions;

  @Value("${dcb.env.code:UNKNOWN}")
  String envCode;

	public AlarmsService(
		AlarmRepository alarmRepository,
    List<NotificationEndpointDefinition> notificationEndpointDefinitions
	){
		this.alarmRepository = alarmRepository;
    this.notificationEndpointDefinitions = notificationEndpointDefinitions;
	}

  @Transactional
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

  @Transactional
	public Mono<Alarm> createNewAlarm(Alarm alarm) {
		alarm.setLastSeen(Instant.now());
		alarm.setCreated(Instant.now());
		alarm.setRepeatCount(Long.valueOf(0));
		return Mono.from(alarmRepository.save(alarm));

	}

	private Mono<String> optionallyNotify(String alarmCode, String status) {
		// Work out the context of the alarm, and see if that context has 
		// email or slack notifications configured, if so send
		log.info("Optionally notify {} : {}",alarmCode,status);
		List<NotificationEndpointDefinition> combined = this.notificationEndpointDefinitions; // + any hostLms specific ones
    return optionallyNotify(combined, alarmCode, status);
  }

  private Mono<String> optionallyNotify(List<NotificationEndpointDefinition> targets, String alarmCode, String status) {
		return Flux.fromIterable( targets )
			.map( target -> {
				log.info("Publish {} {} to {}",alarmCode, status, target);

        if ( target.getProfile() == null ) {
          log.error("Missing profile {}",target);
          return Mono.empty();
        }
          
				
        return switch ( target.getProfile().toUpperCase() ) {
          case "SLACK" -> publishToWebhook(target.getUrl(), mapStringToSlackPayload("DCB-ALARM: "+envCode+" "+alarmCode+" "+status));
          case "TEAMS" -> publishToWebhook(target.getUrl(), mapStringToTeamsPayload("DCB-ALARM: "+envCode+" "+alarmCode+" "+status));
          case "LOG" -> {
            log.info("DCB-ALARM: "+envCode+" "+alarmCode+" "+status);
            yield Mono.empty();
          }
          default -> {
            log.error("Unknown profile for notification {}", target);
            yield Mono.empty();
          }
        };
			})
      .onErrorResume( error -> {
        log.error("Problem notifying alarm", error) ;
        return Mono.empty();
      } )
      .count()
      .doOnNext(count -> log.info("Completed notifying {}",count) )
			.then(Mono.just("OK") );
	}

  private Map<String, Object> mapStringToTeamsPayload(String markdownText) {
      return mapStringToSlackPayload(markdownText);
  }

  private Map<String, Object> mapStringToTeamsPayload(String markdownText, Map params) {
      return mapStringToSlackPayload(markdownText, null);
  }


  private Map<String, Object> mapStringToSlackPayload(String markdownText) {
    return mapStringToSlackPayload(markdownText, null);
  }

  private Map<String, Object> mapStringToSlackPayload(String markdownText, Map<String, Object> params) {

    Map<String, Object> payload = new HashMap<String, Object>();

    payload.put("text", "DCB status message : "+envCode);
  
    List<Object> blocks = new ArrayList();

    blocks.add ( Map.of( "type", "section", "text", Map.of( "type", "mrkdwn", "text", markdownText)));

    if ( ( params != null ) && ( params.size() > 0 ) ) {

      List<Map<String,Object>> fields = new ArrayList<Map<String,Object>>();

      for ( Map.Entry e : params.entrySet() ) {
        Map<String,Object> field = new HashMap<String,Object>();
        field.put ( "type", "mrkdwn");
        field.put ( e.getKey().toString(), e.getValue() );
        fields.add(field);
      }

      blocks.add( Map.of( "type", "section", "fields", fields ) );
    }

    payload.put("blocks", blocks);

    return payload;
  }

	// Prune expired alarms
  @Transactional
	public void prune() {
    Instant now = Instant.now();

    Flux.from(alarmRepository.findByExpiresBefore(now))
				.flatMap(alarm -> 
					optionallyNotify(alarm.getCode(), "DEACTIVATED")
						.map(code -> alarmRepository.delete(alarm.getId()))
				)
        .subscribe();
	}

  @Transactional
	public Mono<String> cancel(String code) {
		log.info("Cancel alarm: {}",code);
		return Mono.from(alarmRepository.deleteByCode(code))
			.then( optionallyNotify(code, "DEACTIVATED") )
			.thenReturn( code );
	}

  public Mono<Void> simpleAnnounce(String markdownMessage) {
    return simpleAnnounce(markdownMessage, null);
  }

  /**
   * Post a simple string message to all system webhooks
   */
  public Mono<Void> simpleAnnounce(String markdownMessage, Map params) {
		return Flux.fromIterable( notificationEndpointDefinitions )
      .flatMap( target -> 
        switch ( target.getProfile() ) {
          case "SLACK" -> publishToWebhook(target.getUrl(), mapStringToSlackPayload(markdownMessage, params));
          case "TEAMS" -> publishToWebhook(target.getUrl(), mapStringToTeamsPayload(markdownMessage, params));
          case "LOG" -> {
            log.info("ANNOUNCE {}",markdownMessage);
            yield Mono.empty();
          }
          default -> {
            log.error("Unknown profile for notification {}", target);
            yield Mono.empty();
          }
        })
      .collectList()
      .then();

  }

	public Mono<Void> publishToWebhook(String url, Map<String,Object> payload) {
		// This is where you get HttpClient programmatically
    if ( ( url == null ) ||
         ( url.trim().length() == 0 ) ) {
      log.error("publish to webhook called with empty URL");
      return Mono.empty();
    }

		try {
			log.info("Announce alarm on webhook : {}",url);

			HttpClient client = HttpClient.create(new URL(url));

			HttpRequest<Map<String, Object>> request = HttpRequest
				.POST(url, payload)
				.contentType(MediaType.APPLICATION_JSON_TYPE);
                
			// Sending the POST request to each webhook URL
			return Mono.from(client.exchange(request))
				.onErrorResume( e -> {
					// Handle the error gracefully, log or return fallback
					log.error("Unable to post to webhook: {} {} {}", e.getMessage(), url.toString(), payload);
					return Mono.empty(); // or a fallback Mono.just(...)
				})
        .doOnNext(next -> log.debug("Webhook called {}", next) )
				.then();
		}
		catch ( Exception e ) {
      log.warn("Problem trying to announce alarm on {} with payload {}",url, payload, e);
			return Mono.empty();
		}
	}

  public void debugConfig() {
    for ( NotificationEndpointDefinition defn : notificationEndpointDefinitions ) {
      log.info("NOTIFICATION ENDPOINT: {}",defn);
    }
  }

  public List<NotificationEndpointDefinition> getEndpoints() {
    return notificationEndpointDefinitions;
  }
}

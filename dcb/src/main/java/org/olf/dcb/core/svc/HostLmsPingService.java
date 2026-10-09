package org.olf.dcb.core.svc;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.storage.HostLmsRepository;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

/** Pings a Host LMS and keeps the result on it, so the last known state outlives the call. */
@Slf4j
@Singleton
public class HostLmsPingService {
	private static final Duration VERSION_CHANGE_ALARM_LIFETIME = Duration.ofDays(7);

	private final HostLmsService hostLmsService;
	private final HostLmsRepository hostLmsRepository;
	private final AlarmsService alarmsService;
	private final ObjectMapper objectMapper;

	public HostLmsPingService(HostLmsService hostLmsService, HostLmsRepository hostLmsRepository,
		AlarmsService alarmsService, ObjectMapper objectMapper) {

		this.hostLmsService = hostLmsService;
		this.hostLmsRepository = hostLmsRepository;
		this.alarmsService = alarmsService;
		this.objectMapper = objectMapper;
	}

	public Mono<PingResponse> pingAndRecord(String code) {
		return hostLmsService.findByCode(code)
			.flatMap(this::pingAndRecord);
	}

	public Mono<PingResponse> pingAndRecord(HostLms hostLms) {
		final var code = hostLms.getCode();

		// ping() catches its own failures, so an error reaching here came from building the client
		return hostLmsService.getClientFor(hostLms)
			.flatMap(client -> ping(client, code))
			.onErrorResume(error -> {
				log.error("Cannot build a client to ping Host LMS {}: {}", code, error.getMessage());
				return Mono.just(PingResponse.misconfigured(code, error));
			})
			.switchIfEmpty(Mono.defer(() -> Mono.just(PingResponse.misconfigured(code,
				new IllegalStateException("DCB could not build a client for this Host LMS")))))
			.flatMap(response -> record(code, response).thenReturn(response));
	}

	private Mono<PingResponse> ping(HostLmsClient client, String code) {
		return client.ping()
			.onErrorResume(error -> Mono.just(PingResponse.error(code, null, error.getMessage(), error,
				Duration.ZERO)));
	}

	/** The last recorded ping for a Host LMS, or empty if it has never been pinged. */
	public Mono<Map<String, Object>> lastPingOf(String code) {
		return Mono.from(hostLmsRepository.findLastPingByCode(code))
			.flatMap(json -> {
				try {
					return Mono.justOrEmpty(objectMapper.readValue(json, Argument.mapOf(String.class, Object.class)));
				}
				catch (IOException e) {
					log.error("Could not read the last ping recorded for Host LMS {}", code, e);
					return Mono.empty();
				}
			});
	}

	private Mono<Void> record(String code, PingResponse response) {
		return lastPingOf(code)
			.defaultIfEmpty(Map.of())
			.flatMap(previous -> raiseIfVersionChanged(code, previous, response)
				.then(write(code, lastPingFrom(previous, response))));
	}

	private Mono<Void> write(String code, Map<String, Object> lastPing) {
		try {
			return Mono.from(hostLmsRepository.updateLastPing(code, objectMapper.writeValueAsString(lastPing)))
				.then();
		}
		catch (IOException e) {
			log.error("Could not record the last ping for Host LMS {}", code, e);
			return Mono.empty();
		}
	}

	static Map<String, Object> lastPingFrom(Map<String, Object> previous, PingResponse response) {
		final var checkedAt = Objects.requireNonNullElse(response.getCheckedAt(), Instant.now()).toString();
		final var lastPing = new HashMap<String, Object>();

		lastPing.put("status", response.getStatus());
		lastPing.put("checkedAt", checkedAt);
		putIfPresent(lastPing, "failure", response.getFailure());
		putIfPresent(lastPing, "versionInfo", response.getVersionInfo());
		putIfPresent(lastPing, "additional", response.getAdditional());
		putIfPresent(lastPing, "facts", response.getFacts());
		putIfPresent(lastPing, "pingMillis",
			response.getPingTime() != null ? response.getPingTime().toMillis() : null);
		putIfPresent(lastPing, "lastOkAt",
			PingResponse.OK.equals(response.getStatus()) ? checkedAt : previous.get("lastOkAt"));

		return lastPing;
	}

	private Mono<Void> raiseIfVersionChanged(String code, Map<String, Object> previous,
		PingResponse response) {

		final var before = previous.get("versionInfo");
		final var after = response.getVersionInfo();

		final var changed = PingResponse.OK.equals(previous.get("status"))
			&& PingResponse.OK.equals(response.getStatus())
			&& before != null && after != null && !before.equals(after);

		if (!changed) {
			return Mono.empty();
		}

		final var alarmCode = "ILS." + code + ".VERSION_CHANGED";

		log.warn("Host LMS {} now reports version {} (was {})", code, after, before);

		return alarmsService.raise(Alarm.builder()
				.id(UUIDUtils.generateAlarmId(alarmCode))
				.code(alarmCode)
				.expires(Instant.now().plus(VERSION_CHANGE_ALARM_LIFETIME))
				.alarmDetails(Map.of("from", before, "to", after))
				.build())
			.then();
	}

	private static void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			map.put(key, value);
		}
	}
}

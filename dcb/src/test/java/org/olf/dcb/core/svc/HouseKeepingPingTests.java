package org.olf.dcb.core.svc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.PingFailure;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.HostLmsRepository;

import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.serde.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class HouseKeepingPingTests {
	private final HostLmsRepository hostLmsRepository = mock(HostLmsRepository.class);
	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final AlarmsService alarmsService = mock(AlarmsService.class);

	private final HouseKeepingService houseKeeping = new HouseKeepingService(mock(R2dbcOperations.class),
		hostLmsRepository, alarmsService, mock(SyslogService.class),
		new HostLmsPingService(hostLmsService, hostLmsRepository, alarmsService, ObjectMapper.getDefault()));

	@BeforeEach
	void beforeEach() {
		when(hostLmsRepository.findLastPingByCode(anyString())).thenReturn(Mono.empty());
		when(hostLmsRepository.updateLastPing(anyString(), anyString())).thenReturn(Mono.just(1L));
	}

	@Test
	void shouldClearRatherThanRaiseAnAlarmForAnAdapterWithNoPing() {
		final var koha = host("KOHA");
		when(hostLmsRepository.queryAll()).thenReturn(Flux.just(koha));
		answersPing(koha, PingResponse.notImplemented("KOHA", "no check"));
		when(alarmsService.cancel(any())).thenReturn(Mono.just("OK"));

		houseKeeping.pingTests().block();

		verify(alarmsService).cancel("ILS.KOHA.PING_FAILURE");
		verify(alarmsService, never()).raise(any());
	}

	@Test
	void shouldStillCheckTheOtherHostsWhenOneCannotBePinged() {
		final var broken = host("BROKEN");
		final var failing = host("FAILING");
		when(hostLmsRepository.queryAll()).thenReturn(Flux.just(broken, failing));
		when(hostLmsService.getClientFor(broken)).thenReturn(Mono.error(new IllegalStateException("no client")));
		answersPing(failing, PingResponse.error("FAILING", null, "down", PingFailure.FAILING, null, Map.of()));
		when(alarmsService.raise(any())).thenReturn(Mono.just(Alarm.builder().build()));

		houseKeeping.pingTests().block();

		verify(alarmsService).raise(argThat(alarm -> "ILS.FAILING.PING_FAILURE".equals(alarm.getCode())));
	}

	@Test
	void shouldRecordAndAlarmAHostWhoseClientCannotBeBuilt() {
		final var broken = host("BROKEN");
		when(hostLmsRepository.queryAll()).thenReturn(Flux.just(broken));
		when(hostLmsService.getClientFor(broken)).thenReturn(Mono.error(new IllegalStateException("no client")));
		when(alarmsService.raise(any())).thenReturn(Mono.just(Alarm.builder().build()));

		houseKeeping.pingTests().block();

		verify(alarmsService).raise(argThat(alarm -> "ILS.BROKEN.PING_FAILURE".equals(alarm.getCode())));
		verify(hostLmsRepository).updateLastPing(eq("BROKEN"),
			argThat(json -> json.contains("\"failure\":\"MISCONFIGURED\"")));
	}

	private void answersPing(DataHostLms hostLms, PingResponse response) {
		final var client = mock(HostLmsClient.class);
		when(client.getHostLmsCode()).thenReturn(hostLms.getCode());
		when(client.ping()).thenReturn(Mono.just(response));
		when(hostLmsService.getClientFor(hostLms)).thenReturn(Mono.just(client));
	}

	private static DataHostLms host(String code) {
		return DataHostLms.builder().id(UUID.randomUUID()).code(code).build();
	}
}

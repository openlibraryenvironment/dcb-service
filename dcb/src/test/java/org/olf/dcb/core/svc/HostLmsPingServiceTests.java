package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import org.olf.dcb.export.ExportHostLmsService;
import org.olf.dcb.export.IngestHostLmsService;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.AlarmRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.test.DataAccess;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import graphql.ExecutionInput;
import graphql.GraphQL;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** The last ping is kept on the Host LMS row, and a version change between two good pings is an alarm. */
@DcbTest
class HostLmsPingServiceTests {
	private static final String CODE = "PINGRECORD";
	private static final String EDITOR = "an-administrator";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private HostLmsRepository hostLmsRepository;
	@Inject
	private AlarmsService alarmsService;
	@Inject
	private AlarmRepository alarmRepository;
	@Inject
	private ObjectMapper objectMapper;
	@Inject
	private GraphQL graphQL;
	@Inject
	private ExportHostLmsService exportHostLmsService;
	@Inject
	private IngestHostLmsService ingestHostLmsService;

	private final DataAccess dataAccess = new DataAccess();
	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final HostLmsClient client = mock(HostLmsClient.class);

	private HostLmsPingService pingService;
	private DataHostLms hostLms;

	@BeforeEach
	void beforeEach() {
		hostLmsFixture.deleteAll();
		dataAccess.deleteAll(alarmRepository.queryAll(), alarm -> alarmRepository.delete(alarm.getId()));

		hostLms = hostLmsFixture.createSierraHostLms(CODE);
		hostLms.setLastEditedBy(EDITOR);
		hostLms = singleValueFrom(hostLmsRepository.update(hostLms));

		when(hostLmsService.getClientFor(any(DataHostLms.class))).thenReturn(Mono.just(client));

		pingService = new HostLmsPingService(hostLmsService, hostLmsRepository, alarmsService, objectMapper);
	}

	@Test
	void shouldKeepTheLastPingOnTheHostLms() {
		answers(PingResponse.ok(CODE, "SIERRA API v6", Duration.ofMillis(42),
			Map.of("tokenExpiresInSeconds", 3599)));

		pingService.pingAndRecord(hostLms).block();

		final var lastPing = lastPing();

		assertThat(lastPing, hasEntry("status", (Object) "OK"));
		assertThat(lastPing, hasEntry("versionInfo", (Object) "SIERRA API v6"));
		assertThat(lastPing, hasEntry("pingMillis", (Object) 42));
		assertThat(lastPing.get("checkedAt"), is(lastPing.get("lastOkAt")));
		assertThat(((Map<?, ?>) lastPing.get("facts")).get("tokenExpiresInSeconds"), is(3599));
	}

	@Test
	void shouldNotRecordAPingAsAnEdit() {
		answers(PingResponse.ok(CODE, "SIERRA API v6", Duration.ZERO));

		pingService.pingAndRecord(hostLms).block();

		final var reloaded = reloaded();

		assertThat(lastPing(), notNullValue());
		assertThat("a ping does not take over who last edited the Host LMS",
			reloaded.getLastEditedBy(), is(EDITOR));
	}

	@Test
	void shouldRememberWhenItWasLastOkThroughAFailure() {
		answers(PingResponse.ok(CODE, "SIERRA API v6", Duration.ZERO));
		pingService.pingAndRecord(hostLms).block();
		final var lastOkAt = lastPing().get("lastOkAt");

		answers(PingResponse.error(CODE, "SIERRA API v6", "down", PingFailure.FAILING, Duration.ZERO, Map.of()));
		pingService.pingAndRecord(hostLms).block();

		final var lastPing = lastPing();

		assertThat(lastPing, hasEntry("status", (Object) "ERROR"));
		assertThat(lastPing, hasEntry("failure", (Object) "FAILING"));
		assertThat(lastPing.get("lastOkAt"), is(lastOkAt));
	}

	@Test
	void shouldRaiseAnAlarmWhenTheVersionChangesBetweenTwoGoodPings() {
		answers(PingResponse.ok(CODE, "POLARIS PAPI 7.6", Duration.ZERO));
		pingService.pingAndRecord(hostLms).block();

		answers(PingResponse.ok(CODE, "POLARIS PAPI 7.7", Duration.ZERO));
		pingService.pingAndRecord(hostLms).block();

		final var alarms = versionChangedAlarms();

		assertThat(alarms, hasSize(1));
		assertThat(alarms.get(0).getAlarmDetails(), hasEntry("from", (Object) "POLARIS PAPI 7.6"));
		assertThat(alarms.get(0).getAlarmDetails(), hasEntry("to", (Object) "POLARIS PAPI 7.7"));
	}

	@Test
	void shouldNotTreatAFailedPingAsAVersionChange() {
		answers(PingResponse.ok(CODE, "POLARIS PAPI 7.6", Duration.ZERO));
		pingService.pingAndRecord(hostLms).block();

		answers(PingResponse.error(CODE, "POLARIS (version unknown)", "down", PingFailure.FAILING,
			Duration.ZERO, Map.of()));
		pingService.pingAndRecord(hostLms).block();

		assertThat(versionChangedAlarms(), hasSize(0));
	}

	@Test
	void shouldServeTheLastPingThroughGraphQL() {
		answers(PingResponse.ok(CODE, "SIERRA API v6", Duration.ZERO));
		pingService.pingAndRecord(hostLms).block();

		final var result = graphQL.execute(ExecutionInput.newExecutionInput()
			.query("query { hostLms(query: \"code:" + CODE + "\") { content { code lastPing } } }")
			.graphQLContext(Map.of("roles", List.of("ADMIN"), "userName", "test"))
			.build());

		assertThat(result.getErrors().toString(), result.getErrors(), hasSize(0));

		final Map<String, Object> data = result.getData();
		final var content = (List<?>) ((Map<?, ?>) data.get("hostLms")).get("content");
		final var lastPing = (Map<?, ?>) ((Map<?, ?>) content.get(0)).get("lastPing");

		assertThat(lastPing.get("status"), is("OK"));
		assertThat(lastPing.get("failure"), nullValue());
	}

	@Test
	void shouldNotLetAHostLmsSavedFromAnOlderCopyOverwriteTheLastPing() {
		final var loadedBeforeThePing = reloaded();

		answers(PingResponse.ok(CODE, "SIERRA API v6", Duration.ZERO));
		pingService.pingAndRecord(hostLms).block();

		singleValueFrom(hostLmsRepository.update(loadedBeforeThePing));

		assertThat(lastPing(), hasEntry("status", (Object) "OK"));
	}

	@Test
	void shouldLeaveTheLastPingOutOfAConfigurationExport() throws Exception {
		answers(PingResponse.ok(CODE, "SIERRA API v6", Duration.ZERO));
		pingService.pingAndRecord(hostLms).block();

		final var siteConfiguration = SiteConfiguration.create();
		exportHostLmsService.export(List.of(hostLms.getId()), new ArrayList<>(), new ArrayList<>(),
			siteConfiguration);

		final var exported = objectMapper.writeValueAsString(siteConfiguration);

		assertThat(exported, containsString(CODE));
		assertThat(exported, not(containsString("lastPing")));
	}

	@Test
	void shouldNotImportALastPing() throws Exception {
		answers(PingResponse.ok(CODE, "SIERRA API v6", Duration.ZERO));
		pingService.pingAndRecord(hostLms).block();

		final var existing = importedCopyOf(reloaded(), hostLms.getId(), CODE);
		final var newCode = CODE + "-NEW";
		final var created = importedCopyOf(reloaded(), UUID.randomUUID(), newCode);

		final var siteConfiguration = SiteConfiguration.create();
		siteConfiguration.lmsHosts.add(existing);
		siteConfiguration.lmsHosts.add(created);

		ingestHostLmsService.ingest(siteConfiguration, new IngestResult());

		assertThat("an existing host keeps its own last ping", lastPing(), hasEntry("status", (Object) "OK"));
		assertThat("a newly imported host starts with none", pingService.lastPingOf(newCode).block(), nullValue());
	}

	private DataHostLms importedCopyOf(DataHostLms source, UUID id, String code) throws Exception {
		final Map<String, Object> json = new HashMap<>(objectMapper.readValue(
			objectMapper.writeValueAsString(source), Argument.mapOf(String.class, Object.class)));

		json.put("id", id.toString());
		json.put("code", code);
		json.put("name", code);
		json.put("lastPing", Map.of("status", "IMPORTED", "versionInfo", "ANOTHER ENVIRONMENT"));

		return objectMapper.readValue(objectMapper.writeValueAsString(json), DataHostLms.class);
	}

	private void answers(PingResponse response) {
		when(client.ping()).thenReturn(Mono.just(response));
	}

	private Map<String, Object> lastPing() {
		return pingService.lastPingOf(CODE).block();
	}

	private DataHostLms reloaded() {
		return singleValueFrom(hostLmsRepository.findByCode(CODE));
	}

	private List<Alarm> versionChangedAlarms() {
		return Flux.from(alarmRepository.queryAll())
			.filter(alarm -> ("ILS." + CODE + ".VERSION_CHANGED").equals(alarm.getCode()))
			.collectList()
			.block();
	}
}

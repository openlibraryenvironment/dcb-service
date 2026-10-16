package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.interaction.SupplierPreflight.Outcome.CANNOT_SUPPLY;
import static org.olf.dcb.core.interaction.SupplierPreflight.Outcome.CLEAR;
import static org.olf.dcb.core.interaction.SupplierPreflight.Outcome.NOT_CHECKED;
import static org.olf.dcb.core.interaction.SupplierPreflight.Outcome.UNREACHABLE;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.SupplierPreflight;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.svc.HostLmsPingService;

import io.micronaut.context.exceptions.BeanInstantiationException;
import reactor.core.publisher.Mono;

/** No database: what each piece of DCB's evidence makes of a supplier. */
class SupplierPreflightServiceTests {
	private static final String SUPPLIER = "SUPPLIER";
	private static final SupplierPreflight.Query QUERY =
		new SupplierPreflight.Query("borrowing-agency", "supplying-agency", "BOOK", "ADULT");

	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final HostLmsPingService pingService = mock(HostLmsPingService.class);
	private final HostLmsClient client = mock(HostLmsClient.class);

	private final SupplierPreflightService service = new SupplierPreflightService(hostLmsService, pingService, true);

	@BeforeEach
	void beforeEach() {
		// One instance serves every test (junit-platform.properties), so the mocks keep earlier calls
		clearInvocations(pingService);
		when(hostLmsService.getClientFor(SUPPLIER)).thenReturn(Mono.just(client));
		when(pingService.lastPingOf(SUPPLIER)).thenReturn(Mono.empty());
		when(client.findLocalPatronType("ADULT")).thenReturn(Mono.just("15"));
		when(client.supplierPreflight(any())).thenReturn(Mono.just(SupplierPreflight.notChecked()));
		when(client.missingConfiguration()).thenReturn(List.of("ingest"));
	}

	@Test
	void shouldCheckNothingWhenTurnedOff() {
		lastPing("REFUSED");
		when(client.findLocalPatronType("ADULT")).thenReturn(Mono.empty());
		clearInvocations(hostLmsService);

		final var off = new SupplierPreflightService(hostLmsService, pingService, false);

		assertThat(off.checkSupplier(SUPPLIER).block().outcome(), is(NOT_CHECKED));
		assertThat(off.check(SUPPLIER, QUERY).block().outcome(), is(NOT_CHECKED));
		verify(hostLmsService, never()).getClientFor(SUPPLIER);
	}

	@Test
	void shouldClearASupplierNeverPinged() {
		assertThat(checkSupplier().outcome(), is(CLEAR));
	}

	@Test
	void shouldNotRuleOutASupplierOverASettingItDeclaresMandatory() {
		assertThat("ingest is declared mandatory but does not stop a supplier", checkSupplier().outcome(), is(CLEAR));
	}

	@Test
	void shouldRuleOutASupplierThatRefusedDcbsCredentials() {
		lastPing("REFUSED");

		final var preflight = checkSupplier();

		assertThat(preflight.outcome(), is(CANNOT_SUPPLY));
		assertThat(preflight.reason(), containsString("refused DCB's credentials"));
		assertThat(preflight.remedy(), containsString("Correct the credentials for Host LMS SUPPLIER"));
		assertThat("the ping's detail is for the supplier's administrators only",
			preflight.reason(), not(containsString("ping detail")));
		verify(pingService, never()).pingAndRecord(SUPPLIER);
	}

	@Test
	void shouldPingAgainBeforeRulingOutOnAnOldRefusal() {
		when(pingService.lastPingOf(SUPPLIER)).thenReturn(
			Mono.just(ping("REFUSED", Instant.now().minus(SupplierPreflightService.RULING_PING_TRUSTED_FOR.plusMinutes(1)))),
			Mono.just(Map.of("status", "OK", "checkedAt", Instant.now().toString())));
		when(pingService.pingAndRecord(SUPPLIER)).thenReturn(Mono.empty());

		assertThat("a refusal fixed since the daily ping does not rule the supplier out",
			checkSupplier().outcome(), is(CLEAR));
		verify(pingService).pingAndRecord(SUPPLIER);
	}

	@Test
	void shouldRuleOutOnAnOldRefusalThatIsConfirmed() {
		when(pingService.lastPingOf(SUPPLIER)).thenReturn(
			Mono.just(ping("REFUSED", Instant.now().minus(SupplierPreflightService.RULING_PING_TRUSTED_FOR.plusMinutes(1)))),
			Mono.just(ping("REFUSED", Instant.now())));
		when(pingService.pingAndRecord(SUPPLIER)).thenReturn(Mono.empty());

		assertThat(checkSupplier().outcome(), is(CANNOT_SUPPLY));
	}

	@Test
	void shouldNotRuleOutOnAnOldRefusalWhenThePingCannotBeRetaken() {
		when(pingService.lastPingOf(SUPPLIER)).thenReturn(
			Mono.just(ping("REFUSED", Instant.now().minus(SupplierPreflightService.RULING_PING_TRUSTED_FOR.plusMinutes(1)))));
		when(pingService.pingAndRecord(SUPPLIER)).thenReturn(Mono.error(new RuntimeException("timed out")));

		assertThat(checkSupplier().outcome(), is(CLEAR));
	}

	@Test
	void shouldNeitherPingAgainNorWarnOverAnOldUnreachableResult() {
		when(pingService.lastPingOf(SUPPLIER)).thenReturn(
			Mono.just(ping("UNREACHABLE", Instant.now().minus(SupplierPreflightService.RULING_PING_TRUSTED_FOR.plusMinutes(1)))));

		assertThat(checkSupplier().outcome(), is(CLEAR));
		verify(pingService, never()).pingAndRecord(SUPPLIER);
	}

	@Test
	void shouldRuleOutASupplierWhoseConfigurationCouldNotBeUsed() {
		lastPing("MISCONFIGURED");

		final var preflight = checkSupplier();

		assertThat(preflight.outcome(), is(CANNOT_SUPPLY));
		assertThat(preflight.remedy(), containsString("Correct the configuration of Host LMS SUPPLIER"));
	}

	@Test
	void shouldOnlyWarnOfASupplierThatWasUnreachable() {
		lastPing("UNREACHABLE");

		assertThat(checkSupplier().outcome(), is(UNREACHABLE));
	}

	@Test
	void shouldClearASupplierWhoseLastPingFailedForAnotherReason() {
		lastPing("FAILING");

		assertThat(checkSupplier().outcome(), is(CLEAR));
	}

	@Test
	void shouldRuleOutASupplierDcbCannotBuildAClientFor() {
		// Micronaut wraps the adapter constructor's exception
		when(hostLmsService.getClientFor(SUPPLIER)).thenReturn(Mono.error(
			new BeanInstantiationException("Error instantiating bean", new IllegalArgumentException("no base-url"))));

		final var preflight = checkSupplier();

		assertThat(preflight.outcome(), is(CANNOT_SUPPLY));
		assertThat(preflight.reason(), containsString("no base-url"));
	}

	@Test
	void shouldNotRuleOutASupplierWhoseRecordCannotBeRead() {
		when(hostLmsService.getClientFor(SUPPLIER)).thenReturn(Mono.error(new RuntimeException("connection pool exhausted")));

		assertThat("a database error is not the supplier's fault", checkSupplier().outcome(), is(CLEAR));
	}

	@Test
	void shouldClearASupplierWhoseLastPingCannotBeRead() {
		when(pingService.lastPingOf(SUPPLIER)).thenReturn(Mono.error(new RuntimeException("database unavailable")));

		assertThat(checkSupplier().outcome(), is(CLEAR));
	}

	@Test
	void shouldRuleOutARequestWhosePatronTypeHasNoMappingAtTheSupplier() {
		when(client.findLocalPatronType("ADULT")).thenReturn(Mono.empty());

		final var preflight = check();

		assertThat(preflight.outcome(), is(CANNOT_SUPPLY));
		assertThat(preflight.remedy(),
			containsString("Add a patron type mapping from DCB patron type ADULT to a patron type at Host LMS SUPPLIER"));
	}

	@Test
	void shouldRuleOutAnUnmappedPatronTypeReportedAsAnError() {
		when(client.findLocalPatronType("ADULT")).thenReturn(Mono.error(new NoPatronTypeMappingFoundException("missing")));

		assertThat(check().outcome(), is(CANNOT_SUPPLY));
	}

	@Test
	void shouldPlaceWhenThePatronTypeCannotBeLookedUp() {
		when(client.findLocalPatronType("ADULT")).thenReturn(Mono.error(new RuntimeException("database unavailable")));

		assertThat(check().outcome(), is(CLEAR));
	}

	@Test
	void shouldTakeTheAdaptersOwnRefusal() {
		when(client.supplierPreflight(any())).thenReturn(Mono.just(SupplierPreflight.cannotSupply("why", "fix")));

		assertThat(check().outcome(), is(CANNOT_SUPPLY));
	}

	@Test
	void shouldKeepTheUnreachableWarningForARequestThatIsOtherwiseClear() {
		lastPing("UNREACHABLE");

		assertThat(check().outcome(), is(UNREACHABLE));
	}

	private SupplierPreflight checkSupplier() {
		return service.checkSupplier(SUPPLIER).block();
	}

	private SupplierPreflight check() {
		return service.check(SUPPLIER, QUERY).block();
	}

	private void lastPing(String failure) {
		when(pingService.lastPingOf(SUPPLIER)).thenReturn(Mono.just(ping(failure, Instant.now())));
	}

	private static Map<String, Object> ping(String failure, Instant checkedAt) {
		return Map.of("status", "ERROR", "failure", failure, "checkedAt", checkedAt.toString(), "additional", "ping detail");
	}
}

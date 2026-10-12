package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

@Property(name = "dcb.requests.preflight-checks.duplicate-requests.enabled", value = "true")
@Property(name = "dcb.requests.preflight-checks.duplicate-requests.active-window", value = "24h")
@DcbTest
class PreventDuplicateRequestsActiveWindowTests extends AbstractPreflightCheckTests {
	private static final String SYSTEM = "duplicate-window-system";
	private static final String PATRON = "duplicate-window-patron";

	@Inject
	private PreventDuplicateRequestsPreFlightCheck check;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private R2dbcOperations r2dbcOperations;

	private final UUID title = UUID.randomUUID();

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldFailWhenAnActiveRequestWasPlacedWithinTheWindow() {
		earlierRequest(CONFIRMED);

		assertThat(check(command()), containsInAnyOrder(failedCheck("DUPLICATE_REQUEST_ATTEMPT")));
	}

	@Test
	void shouldPassWhenTheActiveRequestWasPlacedBeforeTheWindow() {
		placedTwoDaysAgo(earlierRequest(CONFIRMED));

		assertThat(check(command()), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassWhenTheEarlierRequestWithinTheWindowFailed() {
		earlierRequest(ERROR);

		assertThat(check(command()), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassARequestPlacedByStaffWithinTheWindow() {
		earlierRequest(CONFIRMED);

		assertThat(check(command().toBuilder().isPlacedByStaff(true).build()),
			containsInAnyOrder(passedCheck()));
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
	}

	private PlacePatronRequestCommand command() {
		return placeRequestCommand("pickup-location", SYSTEM, PATRON, title);
	}

	private PatronRequest earlierRequest(PatronRequest.Status status) {
		final var hostLms = hostLmsFixture.createSierraHostLms(SYSTEM);
		final var patron = patronFixture.savePatron("home-library");
		final var identity = patronFixture.saveIdentityAndReturn(patron, hostLms, PATRON, true, "-", SYSTEM, null);

		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(UUID.randomUUID())
			.patron(patron)
			.patronHostlmsCode(SYSTEM)
			.requestingIdentity(identity)
			.bibClusterId(title)
			.status(status)
			.build());
	}

	// date_created is @DateCreated, so only an UPDATE can age a request; relative, so no zone applies
	private void placedTwoDaysAgo(PatronRequest request) {
		singleValueFrom(r2dbcOperations.withConnection(connection ->
			Flux.from(connection.createStatement(
					"UPDATE patron_request SET date_created = date_created - interval '2 days' WHERE id = $1")
				.bind("$1", request.getId())
				.execute())
			.flatMap(result -> result.getRowsUpdated())));
	}
}

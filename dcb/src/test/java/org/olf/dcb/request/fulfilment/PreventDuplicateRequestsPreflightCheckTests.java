package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

@Property(name = "dcb.requests.preflight-checks.duplicate-requests.enabled", value = "true")
@DcbTest
class PreventDuplicateRequestsPreflightCheckTests extends AbstractPreflightCheckTests {
	@Inject
	private PreventDuplicateRequestsPreFlightCheck check;

	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	@Inject
	private PatronFixture patronFixture;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldPassWhenNoExistingPatronRequestMatches() {
		// Arrange
		final var command = createPlaceRequestCommand("pickupLocationCode", "requestorHostLmsCode",
			"requestorLocalId", UUID.randomUUID());

		// Act
		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassWhenExistingPatronRequestDoesNotMatchByLocalPatronIdOnly() {
		// Arrange
		final var bibClusterId = UUID.randomUUID();
		final var LOCAL_SYSTEM_CODE = "local-system-code";
		final var LOCAL_ID = "local-identity";
		final var pickupLocationCode = "pickupLocationCode";

		final var homeHostLms = hostLmsFixture.createSierraHostLms(LOCAL_SYSTEM_CODE);
		final var existingPatron = patronFixture.savePatron("home-library");
		final var patronIdentity = patronFixture.saveIdentityAndReturn(existingPatron, homeHostLms,
			LOCAL_ID, true, "-", LOCAL_SYSTEM_CODE, null);

		savePatronRequest(patronIdentity, LOCAL_SYSTEM_CODE, bibClusterId);

		final var command = createPlaceRequestCommand(pickupLocationCode, LOCAL_SYSTEM_CODE,
			"LOCAL_ID_2", bibClusterId);

		// Act
		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassWhenExistingPatronRequestDoesNotMatchByBibClusterIdOnly() {
		// Arrange
		final var bibClusterId = UUID.randomUUID();
		final var LOCAL_SYSTEM_CODE = "local-system-code";
		final var LOCAL_ID = "local-identity";
		final var pickupLocationCode = "pickupLocationCode";

		final var homeHostLms = hostLmsFixture.createSierraHostLms(LOCAL_SYSTEM_CODE);
		final var existingPatron = patronFixture.savePatron("home-library");
		final var patronIdentity = patronFixture.saveIdentityAndReturn(existingPatron, homeHostLms,
			LOCAL_ID, true, "-", LOCAL_SYSTEM_CODE, null);

		savePatronRequest(patronIdentity, LOCAL_SYSTEM_CODE, bibClusterId);

		final var command = createPlaceRequestCommand(pickupLocationCode, LOCAL_SYSTEM_CODE,
			LOCAL_ID, UUID.randomUUID());

		// Act
		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassWhenExistingPatronRequestDoesNotMatchByLocalSystemCodeOnly() {
		// Arrange
		final var bibClusterId = UUID.randomUUID();
		final var LOCAL_SYSTEM_CODE = "local-system-code";
		final var LOCAL_ID = "local-identity";
		final var pickupLocationCode = "pickupLocationCode";

		final var homeHostLms = hostLmsFixture.createSierraHostLms(LOCAL_SYSTEM_CODE);
		final var existingPatron = patronFixture.savePatron("home-library");
		final var patronIdentity = patronFixture.saveIdentityAndReturn(existingPatron, homeHostLms,
			LOCAL_ID, true, "-", LOCAL_SYSTEM_CODE, null);

		savePatronRequest(patronIdentity, LOCAL_SYSTEM_CODE, bibClusterId);

		final var command = createPlaceRequestCommand(pickupLocationCode, "LOCAL_SYSTEM_CODE",
			LOCAL_ID, bibClusterId);

		// Act
		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenExistingPatronRequestMatches() {
		// Arrange
		final var bibClusterId = UUID.randomUUID();
		final var LOCAL_SYSTEM_CODE = "local-system-code";
		final var LOCAL_ID = "local-identity";
		final var pickupLocationCode = "pickupLocationCode";

		final var homeHostLms = hostLmsFixture.createSierraHostLms(LOCAL_SYSTEM_CODE);
		final var existingPatron = patronFixture.savePatron("home-library");
		final var patronIdentity = patronFixture.saveIdentityAndReturn(existingPatron, homeHostLms,
			LOCAL_ID, true, "-", LOCAL_SYSTEM_CODE, null);

		savePatronRequest(patronIdentity, LOCAL_SYSTEM_CODE, bibClusterId);

		final var command = createPlaceRequestCommand(pickupLocationCode, LOCAL_SYSTEM_CODE,
			LOCAL_ID, bibClusterId);

		// Act
		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("DUPLICATE_REQUEST_ATTEMPT",
				"A request already exists for Patron " + LOCAL_ID
					+ " at " + LOCAL_SYSTEM_CODE
					+ " against " + bibClusterId
					+ " to be picked up at " + pickupLocationCode
					+ " within the time window of " + 900 + " seconds.")
		));
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
	}

	private PlacePatronRequestCommand createPlaceRequestCommand(
		String pickupLocationCode, String requestorHostLmsCode,
		String requestorLocalId, UUID bibClusterId)
	{
		return placeRequestCommand(pickupLocationCode, requestorHostLmsCode, requestorLocalId, bibClusterId);
	}

	private void savePatronRequest(PatronIdentity patronIdentity, String localSystemCode, UUID bibClusterId) {
		var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.patronHostlmsCode(localSystemCode)
			.requestingIdentity(patronIdentity)
			.bibClusterId(bibClusterId)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);
	}
}

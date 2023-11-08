package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

@DcbTest()
class PatronRequestPreflightChecksServiceTests extends AbstractPreflightCheckTests {
	@Inject
	private PatronRequestPreflightChecksService preflightChecksService;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private LocationFixture locationFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		// Remove all the state required by the underlying checks
		// This is brittle WRT new checks being introduced
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		locationFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	@Property(name = "dcb.requests.preflight-checks.enabled", value = "false")
	void noChecksShouldBeExecutedWhenDisabled() {
		assertThat("Service instance should not be null", preflightChecksService, is(notNullValue()));

		final var command = placeRequestCommand("any-location-code", "any-context", "any-host-lms-code");

		// Existing checks require state to pass,
		// if they were executed they would fail and this would throw an exception
		assertThat("No checks should be made",
			preflightChecksService.check(command).block(), is(command));
	}
}

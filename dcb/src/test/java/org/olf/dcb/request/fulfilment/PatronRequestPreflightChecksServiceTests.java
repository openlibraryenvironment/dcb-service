package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Property(name = "dcb.requests.preflight-checks.enabled", value = "false")
@Property(name = "include-test-only-check", value = "true")
@MicronautTest(transactional = false, rebuildContext = true)
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

	@Inject
	private Collection<PreflightCheck> preflightChecks;

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
	void noChecksShouldBeExecutedWhenDisabled() {
		assertThat("Service instance should not be null", preflightChecksService, is(notNullValue()));

		final var command = placeRequestCommand("any-location-code", "any-context", "any-host-lms-code");

		// Existing checks require state to pass,
		// if they were executed they would fail and this would throw an exception
		assertThat("No checks should be made",
			preflightChecksService.check(command).block(), is(command));
	}

	@Test
	void allIndividualChecksShouldBeEnabled() {
		assertThat("Pickup location check should be enabled",
			preflightChecks, hasItem(instanceOf(PickupLocationPreflightCheck.class)));

		assertThat("Pickup location to agency mapping check should be enabled",
			preflightChecks, hasItem(instanceOf(PickupLocationToAgencyMappingPreflightCheck.class)));

		assertThat("Patron resolution check should be enabled",
			preflightChecks, hasItem(instanceOf(ResolvePatronPreflightCheck.class)));

		assertThat("Always failing check should be enabled",
			preflightChecks, hasItem(instanceOf(AlwaysFailingCheck.class)));
	}

	@Singleton
	@Requires(property = "include-test-only-check")
	static class AlwaysFailingCheck implements PreflightCheck {
		@Override
		public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
			return Mono.just(List.of(CheckResult.failed("Always fail")));
		}
	}
}

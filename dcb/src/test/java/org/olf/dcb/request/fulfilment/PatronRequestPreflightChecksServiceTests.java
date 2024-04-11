package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.olf.dcb.request.fulfilment.CheckResult.failed;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Property(name = "dcb.requests.preflight-checks.pickup-location.enabled", value = "false")
@Property(name = "dcb.requests.preflight-checks.pickup-location-to-agency-mapping.enabled", value = "false")
@Property(name = "dcb.requests.preflight-checks.resolve-patron.enabled", value = "false")
@Property(name = "include-test-only-check", value = "true")
@DcbTest()
class PatronRequestPreflightChecksServiceTests extends AbstractPreflightCheckTests {
	@Inject
	private Collection<PreflightCheck> preflightChecks;

	@Test
	void shouldOnlyIncludeAlwaysFailingCheck() {
		// Property annotations for class disable the other checks individually
		assertThat("Always failing check should be only enabled check",
			preflightChecks, containsInAnyOrder(instanceOf(AlwaysFailingCheck.class)));
	}

	@Singleton
	@Requires(property = "include-test-only-check")
	static class AlwaysFailingCheck implements PreflightCheck {
		@Override
		public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
			return Mono.just(List.of(failed("ALWAYS_FAIL", "Always fail")));
		}
	}
}

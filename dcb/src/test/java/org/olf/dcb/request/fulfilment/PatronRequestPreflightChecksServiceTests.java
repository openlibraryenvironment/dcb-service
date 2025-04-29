package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ThrowableProblemMatchers.hasParameters;
import static org.olf.dcb.test.matchers.interaction.ProblemMatchers.hasDetail;
import static org.olf.dcb.test.matchers.interaction.ProblemMatchers.hasTitle;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.UnhandledExceptionProblem;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Property(name = "dcb.requests.preflight-checks.pickup-location.enabled", value = "false")
@Property(name = "dcb.requests.preflight-checks.pickup-location-to-agency-mapping.enabled", value = "false")
@Property(name = "dcb.requests.preflight-checks.resolve-patron.enabled", value = "false")
@Property(name = "dcb.requests.preflight-checks.duplicate-requests.enabled", value = "false")
@Property(name = "dcb.requests.preflight-checks.resolve-patron-request.enabled", value = "false")
@Property(name = "dcb.requests.preflight-checks.global-limits.enabled", value = "false")
@Property(name = "include-test-only-check", value = "true")
@DcbTest()
class PatronRequestPreflightChecksServiceTests extends AbstractPreflightCheckTests {
	@Inject
	private Collection<PreflightCheck> preflightChecks;
	@Inject
	private PatronRequestPreflightChecksService service;

	@Test
	void shouldOnlyIncludeAlwaysFailingCheck() {
		// Property annotations for class disable the other checks individually
		assertThat("Always failing check should be only enabled check",
			preflightChecks, containsInAnyOrder(instanceOf(AlwaysFailingCheck.class)));
	}

	@Test
	void shouldHandleUnhandledErrorsDuringPreflight() {
		final var problem = assertThrows(UnhandledExceptionProblem.class,
			() -> singleValueFrom(service.check(PlacePatronRequestCommand.builder().build())));

		assertThat(problem, allOf(
			notNullValue(),
			hasTitle("Unhandled exception: \"Always failing check\""),
			hasDetail("java.lang.RuntimeException: Always failing check"),
			hasParameters(hasEntry(equalTo("stackTrace"), is(not(emptyString()))))
		));
	}

	@Singleton
	@Requires(property = "include-test-only-check")
	static class AlwaysFailingCheck implements PreflightCheck {
		@Override
		public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
			return Mono.error(new RuntimeException("Always failing check"));
		}
	}
}

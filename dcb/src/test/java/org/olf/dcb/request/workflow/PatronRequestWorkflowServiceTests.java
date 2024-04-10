package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataDetail;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasBriefDescription;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasFromStatus;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasToStatus;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasErrorMessage;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronRequestsFixture;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@DcbTest
class PatronRequestWorkflowServiceTests {
	@Inject
	private PatronRequestWorkflowService workflowService;

	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
	}

	@Test
	void shouldTransitionPatronRequestToErrorStatusForException() {
		// Arrange
		final var patronRequestId = randomUUID();

		final var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.status(RESOLVED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		assertThrows(RuntimeException.class, () -> singleValueFrom(
			Mono.just(patronRequest)
				.flatMap(pr -> raiseException("Something went wrong"))
				.transform(workflowService.getErrorTransformerFor(patronRequest))));

		// Assert
		final var updatedPatronRequest = patronRequestsFixture.findById(patronRequestId);

		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(ERROR),
			hasErrorMessage("Something went wrong")
		));

		final var onlyAuditEntry = patronRequestsFixture.findOnlyAuditEntry(
			updatedPatronRequest);

		assertThat(onlyAuditEntry, allOf(
			notNullValue(),
			hasFromStatus(RESOLVED),
			hasToStatus(ERROR),
			hasBriefDescription("Something went wrong")
		));
	}

	@Test
	void shouldTransitionPatronRequestToErrorStatusForProblem() {
		// Arrange
		final var patronRequestId = randomUUID();

		final var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.status(RESOLVED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		assertThrows(ThrowableProblem.class, () -> singleValueFrom(
			Mono.just(patronRequest)
				.flatMap(pr -> raiseProblem("Cannot Perform Operation",
					"Something went wrong", "exampleParameter", "example-value"))
				.transform(workflowService.getErrorTransformerFor(patronRequest))));

		// Assert
		final var updatedPatronRequest = patronRequestsFixture.findById(patronRequestId);

		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(ERROR),
			hasErrorMessage("Cannot Perform Operation")
		));

		final var onlyAuditEntry = patronRequestsFixture.findOnlyAuditEntry(updatedPatronRequest);

		assertThat(onlyAuditEntry, allOf(
			notNullValue(),
			hasFromStatus(RESOLVED),
			hasToStatus(ERROR),
			hasBriefDescription("Cannot Perform Operation"),
			hasAuditDataDetail("Something went wrong"),
			hasAuditDataProperty("exampleParameter", "example-value")
		));
	}

	@Test
	void shouldTransitionPatronRequestToErrorStatusForProblemWithoutDetail() {
		// Arrange
		final var patronRequestId = randomUUID();

		final var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.status(RESOLVED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		assertThrows(ThrowableProblem.class, () -> singleValueFrom(
			Mono.just(patronRequest)
				.flatMap(pr -> raiseProblem("Cannot Perform Operation"))
				.transform(workflowService.getErrorTransformerFor(patronRequest))));

		// Assert
		final var updatedPatronRequest = patronRequestsFixture.findById(patronRequestId);

		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(ERROR),
			hasErrorMessage("Cannot Perform Operation")
		));

		final var onlyAuditEntry = patronRequestsFixture.findOnlyAuditEntry(updatedPatronRequest);

		assertThat(onlyAuditEntry, allOf(
			notNullValue(),
			hasBriefDescription("Cannot Perform Operation"),
			hasProperty("auditData", anEmptyMap())
		));
	}

	@Test
	void shouldTolerateTooLongErrorMessageWhenTransitioningPatronRequestToErrorStatus() {
		// Arrange
		final var patronRequestId = randomUUID();

		final var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.status(RESOLVED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		assertThrows(RuntimeException.class, () -> singleValueFrom(
			Mono.just(patronRequest)
				.flatMap(pr -> raiseException("e".repeat(300)))
				.transform(workflowService.getErrorTransformerFor(patronRequest))));

		// Assert
		final var updatedPatronRequest = patronRequestsFixture.findById(patronRequestId);

		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(ERROR),
			hasErrorMessage("e".repeat(255))
		));
	}

	private static Mono<PatronRequest> raiseException(String message) {
		return Mono.error(new RuntimeException(message));
	}

	private static Mono<PatronRequest> raiseProblem(String title) {
		return Mono.error(Problem.builder()
			.withTitle(title)
			.build());
	}

	private static Mono<PatronRequest> raiseProblem(String title, String detail,
		String parameterKey, String parameterValue) {

		return Mono.error(Problem.builder()
			.withTitle(title)
			.withDetail(detail)
			.with(parameterKey, parameterValue)
			.build());
	}
}

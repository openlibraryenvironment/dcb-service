package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasErrorMessage;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronRequestsFixture;

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
	void shouldTransitionPatronRequestToErrorStatus() {
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
				.flatMap(pr -> raiseError("Something went wrong"))
				.transform(workflowService.getErrorTransformerFor(patronRequest))));

		// Assert
		final var updatedPatronRequest = patronRequestsFixture.findById(patronRequestId);

		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(ERROR),
			hasErrorMessage("Something went wrong")
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
				.flatMap(pr -> raiseError("e".repeat(300)))
				.transform(workflowService.getErrorTransformerFor(patronRequest))));

		// Assert
		final var updatedPatronRequest = patronRequestsFixture.findById(patronRequestId);

		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(ERROR),
			hasErrorMessage("e".repeat(255))
		));
	}

	private static Mono<PatronRequest> raiseError(String message) {
		return Mono.error(new RuntimeException(message));
	}
}

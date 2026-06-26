package org.olf.dcb.storage;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import jakarta.inject.Inject;

@DcbTest
class PatronRequestRepositoryTests {
	@Inject
	private PatronRequestRepository patronRequestRepository;

	@Inject
	private PatronFixture patronFixture;

	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	@BeforeEach
	void beforeEach() {
		patronFixture.deleteAllPatrons();
	}

	@Test
	void scheduledChecksOnlySelectRequestsWithDueNextScheduledPoll() {
		final var patron = patronFixture.savePatron("home-library");
		final var dueRequest = patronRequestsFixture.savePatronRequest(
			PatronRequest.builder()
				.id(randomUUID())
				.patron(patron)
				.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
				.nextScheduledPoll(Instant.now().minusSeconds(60))
				.build());

		patronRequestsFixture.savePatronRequest(
			PatronRequest.builder()
				.id(randomUUID())
				.patron(patron)
				.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
				.nextScheduledPoll(null)
				.build());

		final var scheduledRequestIds = manyValuesFrom(
			patronRequestRepository.findScheduledChecks())
				.stream()
				.map(PatronRequestRepository.ScheduledTrackingRecord::id)
				.toList();

		assertThat(scheduledRequestIds, contains(dueRequest.getId()));
	}
}

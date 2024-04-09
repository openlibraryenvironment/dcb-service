package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasBriefDescription;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasFromStatus;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasNestedAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasToStatus;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronRequestsFixture;

import jakarta.inject.Inject;

@DcbTest
class PatronRequestAuditServiceTests {
	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	@Inject
	private PatronRequestAuditService patronRequestAuditService;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
	}

	@Test
	void shouldSaveAuditRecordForError() {
		// Arrange
		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		singleValueFrom(patronRequestAuditService.addErrorAuditEntry(
			patronRequest, RESOLVED, "Some message",
			Map.of(
				"someProperty", "some value",
				"someNestedProperty", Map.of("otherProperty", "other value")
			)
		));

		// Assert
		final var onlyAuditEntry = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat(onlyAuditEntry, allOf(
			notNullValue(),
			hasBriefDescription("Some message"),
			hasFromStatus(RESOLVED),
			hasToStatus(ERROR),
			hasAuditDataProperty("someProperty", "some value"),
			hasNestedAuditDataProperty("someNestedProperty", "otherProperty", "other value")
		));
	}
}

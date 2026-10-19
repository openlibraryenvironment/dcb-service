package org.olf.dcb.request.notes;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestNote;
import org.olf.dcb.core.model.PatronRequestTag;
import org.olf.dcb.core.model.RequestNoteAuthorKind;
import org.olf.dcb.storage.PatronRequestNoteRepository;
import org.olf.dcb.storage.PatronRequestTagRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestNoteRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestTagRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import jakarta.inject.Inject;

@DcbTest
@TestInstance(PER_CLASS)
class PatronRequestNoteRetentionTests {
	@Inject
	private PatronRequestNoteRetention retention;
	@Inject
	private PatronRequestNoteRepository noteWriter;
	@Inject
	private PatronRequestTagRepository tagWriter;
	@Inject
	private PostgresPatronRequestNoteRepository notes;
	@Inject
	private PostgresPatronRequestTagRepository tags;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	@BeforeEach
	void beforeEach() {
		deleteAll();
	}

	@AfterAll
	void deleteAll() {
		singleValueFrom(notes.deleteAll());
		singleValueFrom(tags.deleteAll());
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
	}

	@Test
	void notesAndTagsGoAYearAfterTheirRequestEndsOrAtOnceWhenItHasGone() {
		final var oldFinalised = requestIn(PatronRequest.Status.FINALISED, Duration.ofDays(400));
		final var oldCancelled = requestIn(PatronRequest.Status.CANCELLED, Duration.ofDays(400));
		final var recentFinalised = requestIn(PatronRequest.Status.FINALISED, Duration.ofDays(30));
		final var oldButOnLoan = requestIn(PatronRequest.Status.LOANED, Duration.ofDays(400));
		final var oldButInError = requestIn(PatronRequest.Status.ERROR, Duration.ofDays(400));
		final var gone = randomUUID();

		for (var request : new UUID[] { oldFinalised, oldCancelled, recentFinalised, oldButOnLoan, oldButInError, gone }) {
			noteOn(request);
			tagOn(request);
		}

		final var removed = singleValueFrom(retention.sweep());

		assertThat(removed.getT1(), is(3L));
		assertThat(removed.getT2(), is(3L));
		assertThat(manyValuesFrom(notes.findAll()).stream().map(PatronRequestNote::getPatronRequestId).toList(),
			containsInAnyOrder(recentFinalised, oldButOnLoan, oldButInError));
		assertThat(manyValuesFrom(tags.findAll()).stream().map(PatronRequestTag::getPatronRequestId).toList(),
			containsInAnyOrder(recentFinalised, oldButOnLoan, oldButInError));
	}

	private UUID requestIn(PatronRequest.Status status, Duration since) {
		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patronFixture.savePatron("home"))
			.status(status)
			.currentStatusTimestamp(Instant.now().minus(since))
			.build()).getId();
	}

	private void noteOn(UUID patronRequestId) {
		singleValueFrom(noteWriter.save(PatronRequestNote.builder()
			.id(randomUUID())
			.patronRequestId(patronRequestId)
			.authorKind(RequestNoteAuthorKind.CONSORTIUM)
			.body("note")
			.dateCreated(Instant.now())
			.build()));
	}

	private void tagOn(UUID patronRequestId) {
		singleValueFrom(tagWriter.save(PatronRequestTag.builder()
			.id(randomUUID())
			.patronRequestId(patronRequestId)
			.tagCode("DAMAGED")
			.authorKind(RequestNoteAuthorKind.CONSORTIUM)
			.dateCreated(Instant.now())
			.build()));
	}
}

package org.olf.dcb.request.notes;

import static org.olf.dcb.core.model.RequestNoteAuthorKind.CONSORTIUM;
import static org.olf.dcb.core.model.RequestNoteAuthorKind.LIBRARY;
import static org.olf.dcb.request.notes.RequestNoteAccess.notYourRequest;

import java.time.Instant;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequestNote;
import org.olf.dcb.storage.PatronRequestNoteRepository;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/** Notes are added or retracted, never edited, so nothing needs a history. */
@Slf4j
@Singleton
public class PatronRequestNoteService {
	public static final int MAX_BODY_LENGTH = 2000;
	// Checked before the insert, so concurrent adds at the limit can each pass it
	public static final int MAX_NOTES_PER_REQUEST = 200;
	public static final int MAX_PAGE_SIZE = 50;

	private final PatronRequestNoteRepository noteRepository;
	private final RequestNoteAccess access;

	public PatronRequestNoteService(PatronRequestNoteRepository noteRepository, RequestNoteAccess access) {
		this.noteRepository = noteRepository;
		this.access = access;
	}

	public Mono<PatronRequestNote> add(UUID patronRequestId, @Nullable String body,
		RequestNoteCaller caller, @Nullable String requestedAgencyCode) {

		final var text = body == null ? "" : body.strip();

		if (text.isEmpty() || text.length() > MAX_BODY_LENGTH) {
			return Mono.error(new HttpStatusException(HttpStatus.BAD_REQUEST,
				"A note must have between 1 and " + MAX_BODY_LENGTH + " characters."));
		}

		return access.authorFor(patronRequestId, caller, requestedAgencyCode)
			.flatMap(author -> Mono.from(noteRepository.countByPatronRequestId(patronRequestId))
				.flatMap(count -> count >= MAX_NOTES_PER_REQUEST
					? Mono.error(new HttpStatusException(HttpStatus.CONFLICT,
						"This request already has the maximum of " + MAX_NOTES_PER_REQUEST + " notes."))
					: Mono.from(noteRepository.save(PatronRequestNote.builder()
						.id(UUID.randomUUID())
						.patronRequestId(patronRequestId)
						.authorKind(author.kind())
						.agencyCode(author.agencyCode())
						.body(text)
						.dateCreated(Instant.now())
						.build())))
				.doOnNext(note -> log.info("Note {} added to patron request {} by {}",
					note.getId(), patronRequestId, author)));
	}

	/** Newest first. Paged because a request may carry up to MAX_NOTES_PER_REQUEST notes. */
	public Mono<Page<PatronRequestNote>> list(UUID patronRequestId, @Nullable Integer pageno,
		@Nullable Integer pagesize) {

		final int page = pageno == null || pageno < 0 ? 0 : pageno;
		final int size = pagesize == null || pagesize < 1 ? MAX_PAGE_SIZE : Math.min(pagesize, MAX_PAGE_SIZE);

		return Mono.from(noteRepository.findByPatronRequestIdOrderByDateCreatedDesc(patronRequestId,
			Pageable.from(page, size)));
	}

	/**
	 * Any user of the library that wrote a note may retract it, since no person is recorded.
	 * The consortium's notes are retracted only by consortium staff.
	 */
	public Mono<PatronRequestNote> retract(UUID noteId, RequestNoteCaller caller) {
		return Mono.from(noteRepository.findById(noteId))
			.switchIfEmpty(Mono.error(() -> caller.consortium()
				? new HttpStatusException(HttpStatus.NOT_FOUND, "No such note.")
				: notYourRequest()))
			.flatMap(note -> {
				if (!mayRetract(note, caller)) {
					return Mono.error(notYourRequest());
				}

				if (note.isRetracted()) {
					return Mono.just(note);
				}

				note.setBody(null)
					.setRetractedAt(Instant.now())
					.setRetractedBy(caller.consortium() ? CONSORTIUM : LIBRARY);

				return Mono.from(noteRepository.update(note))
					.doOnNext(retracted -> log.info("Note {} on patron request {} retracted by {}",
						noteId, retracted.getPatronRequestId(),
						caller.consortium() ? "the consortium" : "library " + retracted.getAgencyCode()));
			});
	}

	private static boolean mayRetract(PatronRequestNote note, RequestNoteCaller caller) {
		return caller.consortium()
			|| (note.getAuthorKind() == LIBRARY && caller.agencyCodes().contains(note.getAgencyCode()));
	}
}

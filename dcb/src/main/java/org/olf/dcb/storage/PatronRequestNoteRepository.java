package org.olf.dcb.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequestNote;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Notes are read one request at a time, through idx_pr_note_request. */
public interface PatronRequestNoteRepository {
	@NonNull
	@SingleResult
	Publisher<? extends PatronRequestNote> save(@Valid @NotNull @NonNull PatronRequestNote note);

	@NonNull
	@SingleResult
	Publisher<? extends PatronRequestNote> update(@Valid @NotNull @NonNull PatronRequestNote note);

	@NonNull
	@SingleResult
	Publisher<PatronRequestNote> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<PatronRequestNote>> findByPatronRequestIdOrderByDateCreatedDesc(
		@NonNull UUID patronRequestId, @NonNull Pageable pageable);

	@NonNull
	@SingleResult
	Publisher<Long> countByPatronRequestId(@NonNull UUID patronRequestId);

	/** Notes whose request is gone, or ended before the cutoff. */
	@NonNull
	@SingleResult
	@Query(value = """
		DELETE FROM patron_request_note n
		WHERE NOT EXISTS (SELECT 1 FROM patron_request pr WHERE pr.id = n.patron_request_id)
		   OR EXISTS (SELECT 1 FROM patron_request pr WHERE pr.id = n.patron_request_id
		     AND pr.status_code IN (:terminalStatuses) AND pr.current_status_timestamp < :endedBefore)
		""", nativeQuery = true)
	Publisher<Long> deleteOrphanedOrEndedBefore(@NonNull Collection<String> terminalStatuses,
		@NonNull Instant endedBefore);
}

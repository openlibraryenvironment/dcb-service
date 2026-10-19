package org.olf.dcb.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequestTag;
import org.olf.dcb.core.model.RequestNoteAuthorKind;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * A library's applications are matched on agency code; the consortium's, which have none, on
 * author kind.
 */
public interface PatronRequestTagRepository {
	@NonNull
	@SingleResult
	Publisher<? extends PatronRequestTag> save(@Valid @NotNull @NonNull PatronRequestTag tag);

	/**
	 * Bounded by the vocabulary times the request's parties. Both author kinds are named so the two
	 * partial unique indexes serve it: no other index leads with patron_request_id.
	 */
	@NonNull
	@Query(value = """
		SELECT * FROM patron_request_tag
		WHERE patron_request_id = :patronRequestId
		  AND (author_kind = 'LIBRARY' OR author_kind = 'CONSORTIUM')
		""", nativeQuery = true)
	Publisher<PatronRequestTag> findByPatronRequestId(@NonNull UUID patronRequestId);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsByPatronRequestIdAndTagCodeAndAgencyCode(@NonNull UUID patronRequestId,
		@NonNull String tagCode, @NonNull String agencyCode);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsByPatronRequestIdAndTagCodeAndAuthorKind(@NonNull UUID patronRequestId,
		@NonNull String tagCode, @NonNull RequestNoteAuthorKind authorKind);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsByTagCode(@NonNull String tagCode);

	@NonNull
	@SingleResult
	Publisher<Long> deleteByPatronRequestIdAndTagCodeAndAgencyCode(@NonNull UUID patronRequestId,
		@NonNull String tagCode, @NonNull String agencyCode);

	@NonNull
	@SingleResult
	Publisher<Long> deleteByPatronRequestIdAndTagCodeAndAuthorKind(@NonNull UUID patronRequestId,
		@NonNull String tagCode, @NonNull RequestNoteAuthorKind authorKind);

	/** Tags whose request is gone, or ended before the cutoff. */
	@NonNull
	@SingleResult
	@Query(value = """
		DELETE FROM patron_request_tag t
		WHERE NOT EXISTS (SELECT 1 FROM patron_request pr WHERE pr.id = t.patron_request_id)
		   OR EXISTS (SELECT 1 FROM patron_request pr WHERE pr.id = t.patron_request_id
		     AND pr.status_code IN (:terminalStatuses) AND pr.current_status_timestamp < :endedBefore)
		""", nativeQuery = true)
	Publisher<Long> deleteOrphanedOrEndedBefore(@NonNull Collection<String> terminalStatuses,
		@NonNull Instant endedBefore);
}

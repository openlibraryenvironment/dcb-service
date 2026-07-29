package org.olf.dcb.request.lifecycle.ncip.profile.persistence;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import java.time.Instant;
import java.util.UUID;
import org.olf.dcb.request.lifecycle.ncip.profile.domain.DcbProfileMembership;
import org.reactivestreams.Publisher;

public interface DcbProfileMembershipRepository {
	@NonNull
	@SingleResult
	Publisher<? extends DcbProfileMembership> save(@NonNull DcbProfileMembership membership);

	@NonNull
	@SingleResult
	Publisher<? extends DcbProfileMembership> update(@NonNull DcbProfileMembership membership);

	@NonNull
	@SingleResult
	Publisher<DcbProfileMembership> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<DcbProfileMembership> findByTokenHash(@NonNull String tokenHash);

	@Query(value = "SELECT * FROM dcb_profile_membership WHERE token_hash = :tokenHash FOR UPDATE", nativeQuery = true)
	@SingleResult
	Publisher<DcbProfileMembership> findByTokenHashForUpdate(@NonNull String tokenHash);

	@Query(value = """
		SELECT * FROM dcb_profile_membership
		WHERE state IN ('ACTIVE', 'REVIEW_REQUIRED')
		  AND (next_sync_at IS NULL OR next_sync_at <= :now)
		ORDER BY next_sync_at NULLS FIRST
		LIMIT :limit
		""", nativeQuery = true)
	Publisher<DcbProfileMembership> findDueForSync(@NonNull Instant now, int limit);
}

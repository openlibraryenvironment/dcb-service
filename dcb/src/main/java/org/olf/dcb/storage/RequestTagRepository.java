package org.olf.dcb.storage;

import org.olf.dcb.core.model.RequestTag;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface RequestTagRepository {
	@NonNull
	@SingleResult
	Publisher<? extends RequestTag> save(@Valid @NotNull @NonNull RequestTag tag);

	@NonNull
	@SingleResult
	Publisher<? extends RequestTag> update(@Valid @NotNull @NonNull RequestTag tag);

	@NonNull
	@SingleResult
	Publisher<RequestTag> findById(@NonNull String code);

	/** The whole vocabulary, which RequestTagService caps. */
	@NonNull
	Publisher<RequestTag> listOrderByLabel();

	@NonNull
	@SingleResult
	Publisher<Long> count();

	/** Deletes the tag only while no request carries it: no row is deleted when one does. */
	@NonNull
	@SingleResult
	@Query(value = """
		DELETE FROM request_tag rt
		WHERE rt.code = :code
		  AND NOT EXISTS (SELECT 1 FROM patron_request_tag t WHERE t.tag_code = rt.code)
		""", nativeQuery = true)
	Publisher<Long> deleteUnused(@NonNull String code);
}

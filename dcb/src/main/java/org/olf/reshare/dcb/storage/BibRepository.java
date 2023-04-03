package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

public interface BibRepository {

	@NonNull
	@SingleResult
	Publisher<? extends BibRecord> save(@Valid @NotNull @NonNull BibRecord bibRecord);

	@NonNull
	@SingleResult
	Publisher<? extends BibRecord> update(@Valid @NotNull @NonNull BibRecord bibRecord);

	@NonNull
	@SingleResult
	Publisher<BibRecord> findById(@NonNull UUID id);

	@NonNull
	Publisher<BibRecord> findAll();
	
	// @NonNull
	// Publisher<BibRecord> findAllByContributesTo(@NonNull UUID clusterRecordId) {
	// }
	
	Publisher<ClusterRecord> findContributesToById( @NonNull UUID id );
	
	@NonNull
	default Publisher<BibRecord> findAllByContributesTo(ClusterRecord clusterRecord) {
		return findAllByContributesTo( clusterRecord );
	}

	@NonNull
	@SingleResult
	Publisher<Page<BibRecord>> findAll(Pageable page);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);
	
	@NonNull
	@SingleResult
	public Publisher<Void> cleanUp();
	
	@NonNull
	@SingleResult
	public Publisher<Void> commit();
}

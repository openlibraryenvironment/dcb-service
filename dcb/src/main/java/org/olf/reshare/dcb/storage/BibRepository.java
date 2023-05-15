package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import io.micronaut.data.annotation.Query;
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
	
	Publisher<ClusterRecord> findContributesToById( @NonNull UUID id );
	
	@NonNull
	Publisher<BibRecord> findAllByContributesTo(ClusterRecord clusterRecord);

	@NonNull
	@SingleResult
	Publisher<Page<BibRecord>> findAll(Pageable page);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Void> delete(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Void> cleanUp();
	
	@NonNull
	@SingleResult
	Publisher<Void> commit();

	// @Query(value = "SELECT b.contributes_to from bib_record b join bib_identifier bi on ( bi.owner_id = b.id ) where bi.value = :blockingTitle and bi.namespace='BLOCKING_TITLE' limit 1", nativeQuery = true)

	@Query(value = "SELECT cr.* from bib_record b join bib_identifier bi on ( bi.owner_id = b.id ) join cluster_record cr on (cr.id = b.contributes_to) where bi.value = :blockingTitle and bi.namespace='BLOCKING_TITLE' limit 1", nativeQuery = true)
	Publisher<ClusterRecord> findContributesToByBlockingTitle(String blockingTitle);


	@Query(value = "SELECT b.* from bib_record b where b.contributes_to = :id order by b.metadata_score desc limit 1", nativeQuery = true)
	Publisher<BibRecord> findFirstBibRecordInClusterByHighestScore(@NonNull UUID id );

	@Query(value="select b.id from bib_record b where b.contributes_to = :clusterId", nativeQuery = true)
	Publisher<UUID> findBibIdsForCluster(@NonNull UUID clusterId);

	@Query(value = "SELECT cr.* from bib_record b join bib_identifier bi on ( bi.owner_id = b.id ) join cluster_record cr on (cr.id = b.contributes_to) where bi.value = :identifierStr and bi.namespace=:namespace limit 1", nativeQuery = true)
	Publisher<ClusterRecord>  findContributesToIdAndNS(String identifierStr, String namespace);
}

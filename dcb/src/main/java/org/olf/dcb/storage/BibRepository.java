package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.RecordCountSummary;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

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
	@SingleResult
	@Join(value = "contributesTo")
	Publisher<BibRecord> getById(@NonNull UUID id);

	@NonNull
	Publisher<BibRecord> queryAll();

	Publisher<ClusterRecord> findContributesToById(@NonNull UUID id);

	@NonNull
	Publisher<BibRecord> findAllByContributesTo(ClusterRecord clusterRecord);
	
	@NonNull
	Publisher<BibRecord> findAllByContributesToInList(@NonNull Collection<ClusterRecord> contributesToList);
	
	@NonNull
	Publisher<BibRecord> findTop2ByContributesToOrderByMetadataScoreDesc (ClusterRecord clusterRecord);

	@NonNull
	Publisher<Page<BibRecord>> findAllByContributesTo( ClusterRecord clusterRecord, Pageable page );

	@NonNull
	@SingleResult
	Publisher<Page<BibRecord>> findAllBySourceSystemId( UUID sourceSystemId, Pageable page );

	@NonNull
	@SingleResult
	Publisher<Page<BibRecord>> queryAll(Pageable page);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Void> delete(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Void> updateByContributesToInList(@NonNull Collection<ClusterRecord> contributesToList,
			@NonNull ClusterRecord contributesTo);

	@NonNull
	@SingleResult
	Publisher<Void> cleanUp();

	@NonNull
	@SingleResult
	Publisher<Void> commit();

	// @Query(value = "SELECT b.contributes_to from bib_record b join bib_identifier
	// bi on ( bi.owner_id = b.id ) where bi.value = :blockingTitle and
	// bi.namespace='BLOCKING_TITLE' limit 1", nativeQuery = true)

//	@Query(value = "SELECT cr.* from bib_record b join bib_identifier bi on ( bi.owner_id = b.id ) join cluster_record cr on (cr.id = b.contributes_to) where bi.value = :blockingTitle and bi.namespace='BLOCKING_TITLE' limit 1", nativeQuery = true)
//	Publisher<ClusterRecord> findContributesToByBlockingTitle(String blockingTitle);

//	@Query(value = "SELECT b.* from bib_record b where b.contributes_to = :id order by b.metadata_score desc limit 1", nativeQuery = true)
//	Publisher<BibRecord> findFirstBibRecordInClusterByHighestScore(@NonNull UUID id);

//	@Query(value = "select b.id from bib_record b where b.contributes_to = :clusterId", nativeQuery = true)
//	Publisher<UUID> findBibIdsForCluster(@NonNull UUID clusterId);
//
//	@Query(value = "SELECT cr.* from bib_record b join bib_identifier bi on ( bi.owner_id = b.id ) join cluster_record cr on (cr.id = b.contributes_to) where bi.value = :identifierStr and bi.namespace=:namespace limit 1", nativeQuery = true)
//	Publisher<ClusterRecord> findContributesToIdAndNS(String identifierStr, String namespace);

	@Query(value = "select b.id as bibId, b.title as title, b.source_record_id as sourceRecordId, b.metadata_score as metadataScore, b.cluster_reason as clusterReason, h.code as sourceSystem from bib_record b, host_lms h where b.source_system_id = h.id and b.contributes_to = :clusterId", nativeQuery = true)
	Publisher<MemberBib> findMemberBibsForCluster(@NonNull UUID clusterId);

	@Introspected
	public record MemberBib(UUID bibid, @Nullable String title, String sourcerecordid, @Nullable String metadatascore,
			@Nullable String clusterreason, String sourcesystem) {
	};

	@Query(value = "select a.id as source_system_id, a.name as source_system_name, sq.total as record_count from ( select source_system_id id, count(*) total from bib_record group by source_system_id ) sq, host_lms a where sq.id = a.id", nativeQuery = true)
  public Publisher<RecordCountSummary> getIngestReport();
}

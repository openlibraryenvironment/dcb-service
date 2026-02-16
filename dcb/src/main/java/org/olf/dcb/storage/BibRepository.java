package org.olf.dcb.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.clustering.RecordClusteringService.MissingAvailabilityInfo;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.RecordCountSummary;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Vetoed;
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
	@Join(value = "contributesTo")
	Publisher<BibRecord> getAllByIdIn( @NonNull Collection<UUID> ids);
	
	@NonNull
	Publisher<BibRecord> findAllByIdIn( @NonNull Collection<UUID> ids );

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
	Publisher<BibRecord> findAllByContributesToId(UUID id);
	
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
	@Vetoed
	Publisher<Void> updateByContributesToInList(@NonNull Collection<ClusterRecord> contributesToList,
			@NonNull ClusterRecord contributesTo);


	@Vetoed
	@NonNull
	@SingleResult
	Publisher<Void> cleanUp();

	@Vetoed
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
	
	public Publisher<BibRecord> findTop1000ByContributesToIsNullAndSourceRecordUuidIsNull();

	@Query(value = "select a.id as source_system_id, a.name as source_system_name, sq.total as record_count from ( select source_system_id id, count(*) total from bib_record group by source_system_id ) sq, host_lms a where sq.id = a.id", nativeQuery = true)
	public Publisher<RecordCountSummary> getIngestReport();
	
	@SingleResult
	@Query(value = "select count(*) count from bib_record where source_system_id = :hostLmsId", nativeQuery = true)
	public Publisher<Long> getCountForHostLms(UUID hostLmsId);
	
	@Vetoed
	public Publisher<MissingAvailabilityInfo> findMissingAvailability ( int limit, Instant graceCutoff );
	
	@Query(value = """
select mp.value as match_point_value,
       mp.domain as match_point_domain,
       br.id as bib_id,
       br.title,
       br.process_version as process_version,
       hl.name as host_name,
       (select count(*) from match_point where bib_id = br.id) as number_of_match_points
from match_point mp, bib_record br, host_lms hl
where hl.id = br.source_system_id and
      mp.bib_id = br.id and
      br.contributes_to = :clusterId
order by br.id
""", nativeQuery = true)
	Publisher<BibMatchPointDetail> findMatchPointDetailsFor(@NonNull UUID clusterId);

	@Query(value = """
select distinct bi.value, bi.namespace
from bib_identifier bi,
     bib_record br
where bi.owner_id = br.id and
      upper(bi.namespace) in ( :namespaces ) and
      br.contributes_to = :clusterId
""", nativeQuery = true)
	Publisher<Identifier> findDistinctIdentifiersFor(@NonNull UUID clusterId, @NonNull List<String> namespaces);

	@Introspected
	public static record MemberBib(UUID bibid, @Nullable String title, String sourcerecordid, @Nullable String metadatascore,
			@Nullable String clusterreason, String sourcesystem) {
	};

	@Introspected
	public static record BibMatchPointDetail(
		UUID bibId,
		UUID matchPointValue,
		@Nullable String title,
		@Nullable String matchPointDomain,
		Integer processVersion,
		String hostName,
		Integer numberOfMatchPoints
	) {
	};

	@Introspected
	public static record Identifier(
		String namespace,
		String value
	) {
	};
}

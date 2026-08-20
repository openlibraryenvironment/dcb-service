package org.olf.dcb.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.api.serde.ClusterSizeStat;
import org.olf.dcb.core.api.serde.CollectionOverlapStat;
import org.olf.dcb.core.api.serde.CollectionProfileStat;
import org.olf.dcb.core.api.serde.CollectionTotalsStat;
import org.olf.dcb.core.api.serde.SourceFormatStat;
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

	@NonNull
	@SingleResult
	Publisher<BibRecord> findBySourceSystemIdAndSourceRecordId(@NonNull UUID sourceSystemId, @NonNull String sourceRecordId);

	// --- Collection analysis -------------------------------------------------------------
	// These describe the catalogued collection and touch neither patron_request nor live
	// availability, so they report before any request is placed. Semantics, bounds and access
	// controls: docs/insights.md part 5.
	//
	// ALL of them share one intermediate - the DISTINCT (cluster, source system) pairs - so the
	// figures reconcile against each other on one screen. DISTINCT is load-bearing: one source
	// can contribute several bibs to a cluster, and without it every count here is inflated.
	//
	// Each is a full aggregate over bib_record (20M rows), served through
	// CollectionAnalysisService, which limits concurrency and caches. Do not call them directly.

	// Per-source collection shape. A LEFT JOIN would be wrong: a bib with no cluster has no
	// comparable work identity, so unclustered bibs are excluded rather than counted as unique.
	@Query(value = """
WITH cluster_source AS (
    SELECT DISTINCT b.contributes_to AS cluster_id, b.source_system_id
    FROM bib_record b
    JOIN cluster_record cr ON cr.id = b.contributes_to
    WHERE cr.is_deleted = false
),
cluster_holders AS (
    SELECT cluster_id, COUNT(*) AS holder_count
    FROM cluster_source
    GROUP BY cluster_id
)
SELECT h.id AS source_system_id,
       h.code AS source_system_code,
       COUNT(*) AS cluster_count,
       COUNT(*) FILTER (WHERE ch.holder_count = 1) AS unique_title_count
FROM cluster_source cs
JOIN cluster_holders ch ON ch.cluster_id = cs.cluster_id
JOIN host_lms h ON h.id = cs.source_system_id
GROUP BY 1, 2
ORDER BY 3 DESC
""", nativeQuery = true)
	Publisher<CollectionProfileStat> getCollectionProfile();

	// Pairwise duplication, for ONE library against all others. Never restore the full matrix:
	// it self-joins the intermediate unrestricted, so a work held by k sources emits k(k-1)/2
	// pairs, summed over every work in the consortium. At 500 members one widely-held title
	// alone produces over 100,000 rows, and popular titles are precisely the widely-held ones.
	// An outer LIMIT cannot save it - the pairs are built before they are ordered.
	//
	// libraryCode is comma separated like every other scoped query, so a caller administering
	// several libraries gets each of them paired against everyone else.
	@Query(value = """
WITH cluster_source AS (
    SELECT DISTINCT b.contributes_to AS cluster_id, b.source_system_id
    FROM bib_record b
    JOIN cluster_record cr ON cr.id = b.contributes_to
    WHERE cr.is_deleted = false
),
mine AS (
    SELECT cs.cluster_id, cs.source_system_id
    FROM cluster_source cs
    JOIN host_lms h ON h.id = cs.source_system_id
    WHERE h.code = ANY(string_to_array(:libraryCode, ','))
)
SELECT m.source_system_id AS left_system_id,
       lh.code AS left_system_code,
       cs.source_system_id AS right_system_id,
       rh.code AS right_system_code,
       COUNT(*) AS shared_title_count
FROM mine m
JOIN cluster_source cs ON cs.cluster_id = m.cluster_id
    AND cs.source_system_id <> m.source_system_id
JOIN host_lms lh ON lh.id = m.source_system_id
JOIN host_lms rh ON rh.id = cs.source_system_id
GROUP BY 1, 2, 3, 4
ORDER BY 5 DESC
""", nativeQuery = true)
	Publisher<CollectionOverlapStat> getCollectionOverlapForLibrary(String libraryCode);

	// How many source systems hold each work - and the honesty check on the counts above: if
	// this is overwhelmingly holder_count = 1, the matching is under-clustering and every
	// unique-title figure is fiction. Ship it beside them, never after.
	@Query(value = """
WITH cluster_source AS (
    SELECT DISTINCT b.contributes_to AS cluster_id, b.source_system_id
    FROM bib_record b
    JOIN cluster_record cr ON cr.id = b.contributes_to
    WHERE cr.is_deleted = false
),
cluster_holders AS (
    SELECT cluster_id, COUNT(*) AS holder_count
    FROM cluster_source
    GROUP BY cluster_id
)
SELECT holder_count, COUNT(*) AS cluster_count
FROM cluster_holders
GROUP BY 1
ORDER BY 1
""", nativeQuery = true)
	Publisher<ClusterSizeStat> getClusterSizeDistribution();

	// Format mix per source, counted per WORK on the same intermediate as everything above.
	// Counting bib_record rows would put a per-record number beside per-work numbers on one
	// panel: a source cataloguing one work four times would report four times the format.
	//
	// derived_type joins the DISTINCT key rather than being aggregated, because one source can
	// legitimately contribute a print and a large-print edition to the same work.
	@Query(value = """
WITH cluster_source_format AS (
    SELECT DISTINCT b.contributes_to AS cluster_id, b.source_system_id, b.derived_type
    FROM bib_record b
    JOIN cluster_record cr ON cr.id = b.contributes_to
    WHERE cr.is_deleted = false
)
SELECT h.id AS source_system_id,
       h.code AS source_system_code,
       csf.derived_type,
       COUNT(*) AS title_count
FROM cluster_source_format csf
JOIN host_lms h ON h.id = csf.source_system_id
GROUP BY 1, 2, 3
ORDER BY 4 DESC
""", nativeQuery = true)
	Publisher<SourceFormatStat> getFormatProfile();

	// The consortium headline, as a query rather than something the caller derives: distinct
	// titles is NOT the sum of getCollectionProfile's cluster_count. A title held by three
	// libraries appears in three of those rows, so that sum is holdings - a plausible number
	// that is simply too big, with nothing on the page to say so.
	@SingleResult
	@Query(value = """
WITH cluster_source AS (
    SELECT DISTINCT b.contributes_to AS cluster_id, b.source_system_id
    FROM bib_record b
    JOIN cluster_record cr ON cr.id = b.contributes_to
    WHERE cr.is_deleted = false
),
cluster_holders AS (
    SELECT cluster_id, COUNT(*) AS holder_count
    FROM cluster_source
    GROUP BY cluster_id
)
SELECT (SELECT COUNT(*) FROM cluster_holders)                        AS distinct_titles,
       (SELECT COUNT(*) FROM cluster_holders WHERE holder_count = 1) AS singly_held_titles,
       (SELECT COUNT(*) FROM cluster_source)                         AS holdings,
       (SELECT COUNT(DISTINCT source_system_id) FROM cluster_source) AS contributing_sources
""", nativeQuery = true)
	Publisher<CollectionTotalsStat> getCollectionTotals();

}

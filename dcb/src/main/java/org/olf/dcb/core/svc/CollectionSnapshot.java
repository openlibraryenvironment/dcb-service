package org.olf.dcb.core.svc;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import java.util.Comparator;
import java.util.List;

import org.olf.dcb.core.api.serde.ClusterSizeStat;
import org.olf.dcb.core.api.serde.CollectionProfileStat;
import org.olf.dcb.core.api.serde.CollectionTotalsStat;
import org.olf.dcb.core.api.serde.SourceFormatStat;
import org.olf.dcb.core.model.CollectionSnapshotRow;

/** The four consortium-wide collection figures, from the one pass that produces them all. */
public record CollectionSnapshot(
	CollectionTotalsStat totals,
	List<CollectionProfileStat> profile,
	List<ClusterSizeStat> clusterSizes,
	List<SourceFormatStat> formats) {

	private static final Comparator<CollectionProfileStat> MOST_WORKS_FIRST =
		comparing(CollectionProfileStat::clusterCount).reversed()
			.thenComparing(CollectionProfileStat::sourceSystemCode);

	private static final Comparator<SourceFormatStat> MOST_TITLES_FIRST =
		comparing(SourceFormatStat::titleCount).reversed()
			.thenComparing(SourceFormatStat::sourceSystemCode)
			.thenComparing(SourceFormatStat::derivedType, nullsFirst(naturalOrder()));

	public static CollectionSnapshot from(List<CollectionSnapshotRow> rows) {
		final var profile = rows.stream()
			.filter(row -> "PROFILE".equals(row.kind()))
			.map(row -> new CollectionProfileStat(row.sourceSystemId(), row.sourceSystemCode(),
				row.itemCount(), row.uniqueCount()))
			.sorted(MOST_WORKS_FIRST)
			.toList();

		final var formats = rows.stream()
			.filter(row -> "FORMAT".equals(row.kind()))
			.map(row -> new SourceFormatStat(row.sourceSystemId(), row.sourceSystemCode(),
				row.derivedType(), row.itemCount()))
			.sorted(MOST_TITLES_FIRST)
			.toList();

		final var clusterSizes = rows.stream()
			.filter(row -> "HOLDERS".equals(row.kind()))
			.map(row -> new ClusterSizeStat(row.holderCount().intValue(), row.itemCount()))
			.sorted(comparing(ClusterSizeStat::holderCount))
			.toList();

		final var holdings = rows.stream()
			.filter(row -> "HOLDINGS".equals(row.kind()))
			.findFirst();

		// Distinct titles is the number of clusters, not the sum of the profile: a title held by
		// three libraries appears in three profile rows.
		final var totals = new CollectionTotalsStat(
			clusterSizes.stream().mapToLong(ClusterSizeStat::clusterCount).sum(),
			clusterSizes.stream()
				.filter(size -> size.holderCount() == 1)
				.mapToLong(ClusterSizeStat::clusterCount)
				.sum(),
			holdings.map(CollectionSnapshotRow::itemCount).orElse(0L),
			holdings.map(CollectionSnapshotRow::uniqueCount).orElse(0L));

		return new CollectionSnapshot(totals, profile, clusterSizes, formats);
	}
}

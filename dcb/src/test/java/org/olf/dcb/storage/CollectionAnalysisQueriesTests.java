package org.olf.dcb.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.serde.ClusterSizeStat;
import org.olf.dcb.core.api.serde.CollectionOverlapStat;
import org.olf.dcb.core.api.serde.CollectionProfileStat;
import org.olf.dcb.core.api.serde.SourceFormatStat;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;

// These queries count WORKS rather than rows, so the cases that matter are the ones where those
// two differ: a source contributing several bibs to one cluster, and clusters held by several
// sources. Semantics: docs/insights.md part 5.
@DcbTest
class CollectionAnalysisQueriesTests {
	@Inject
	private BibRecordFixture bibRecordFixture;

	@Inject
	private ClusterRecordFixture clusterRecordFixture;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private BibRepository bibRepository;

	private DataHostLms libA;
	private DataHostLms libB;
	private DataHostLms libC;

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
		hostLmsFixture.deleteAll();

		// The collection analysis queries report host LMS code. HostLmsFixture names every host
		// LMS "Test Host LMS", so asserting on name could never distinguish these three - but
		// code is the right thing to report anyway, so the queries changed rather than the
		// fixture.
		libA = hostLmsFixture.createDummyHostLms("LIB_A");
		libB = hostLmsFixture.createDummyHostLms("LIB_B");
		libC = hostLmsFixture.createDummyHostLms("LIB_C");
	}

	private ClusterRecord cluster() {
		return clusterRecordFixture.createClusterRecord(UUID.randomUUID(), UUID.randomUUID());
	}

	private ClusterRecord deletedCluster() {
		return clusterRecordFixture.createClusterRecord(
			ClusterRecord.builder()
				.id(UUID.randomUUID())
				.title("Withdrawn")
				.selectedBib(UUID.randomUUID())
				.isDeleted(true)
				.build());
	}

	private void bib(DataHostLms source, ClusterRecord cluster) {
		bibRecordFixture.createBibRecord(UUID.randomUUID(), source.getId(),
			"src-" + UUID.randomUUID(), cluster);
	}

	private void bibWithoutDerivedType(DataHostLms source, ClusterRecord cluster) {
		bibRecordFixture.createBibRecord(UUID.randomUUID(), source.getId(),
			"src-" + UUID.randomUUID(), cluster, null);
	}

	private CollectionProfileStat profileFor(List<CollectionProfileStat> all, DataHostLms lms) {
		return all.stream()
			.filter(stat -> stat.sourceSystemId().equals(lms.getId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No profile for " + lms.getCode()));
	}

	private SourceFormatStat formatFor(List<SourceFormatStat> all, DataHostLms lms) {
		return all.stream()
			.filter(stat -> stat.sourceSystemId().equals(lms.getId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No format profile for " + lms.getCode()));
	}

	@Test
	void uniqueHoldingsAreClustersNoOtherSourceContributesTo() {
		// Held by A only - a genuine unique holding.
		final var soloCluster = cluster();
		bib(libA, soloCluster);

		// Held by A and B - unique to neither.
		final var sharedCluster = cluster();
		bib(libA, sharedCluster);
		bib(libB, sharedCluster);

		// Held by B only.
		final var bOnlyCluster = cluster();
		bib(libB, bOnlyCluster);

		final var profiles = manyValuesFrom(bibRepository.getCollectionProfile());

		final var aProfile = profileFor(profiles, libA);
		assertThat(aProfile.clusterCount(), equalTo(2L));
		assertThat(aProfile.uniqueTitleCount(), equalTo(1L));

		final var bProfile = profileFor(profiles, libB);
		assertThat(bProfile.clusterCount(), equalTo(2L));
		assertThat(bProfile.uniqueTitleCount(), equalTo(1L));

		// C ingested nothing, so it must not appear at all rather than appear as a zero.
		assertThat(profiles, hasSize(2));
	}

	@Test
	void multipleBibsFromOneSourceInAClusterCountAsOneWork() {
		// The duplicate-bib case: without DISTINCT on (cluster, source) this reads as two works,
		// and the cluster looks multiply-held so the unique count silently drops to zero.
		final var singleCluster = cluster();
		bib(libA, singleCluster);
		bib(libA, singleCluster);
		bib(libA, singleCluster);

		final var profiles = manyValuesFrom(bibRepository.getCollectionProfile());

		final var aProfile = profileFor(profiles, libA);
		assertThat(aProfile.clusterCount(), equalTo(1L));
		assertThat(aProfile.uniqueTitleCount(), equalTo(1L));
	}

	@Test
	void deletedClustersAreExcludedFromTheProfile() {
		final var liveCluster = cluster();
		bib(libA, liveCluster);

		bib(libA, deletedCluster());

		final var profiles = manyValuesFrom(bibRepository.getCollectionProfile());

		assertThat(profileFor(profiles, libA).clusterCount(), equalTo(1L));
	}

	@Test
	void overlapReportsOneRowPerPeerHoldingTheCallersWorks() {
		// One work held by all three.
		final var everyone = cluster();
		bib(libA, everyone);
		bib(libB, everyone);
		bib(libC, everyone);

		// A second work held by A and B only, so B should show 2 shared works and C only 1.
		final var aAndB = cluster();
		bib(libA, aAndB);
		bib(libB, aAndB);

		final var overlaps = manyValuesFrom(
			bibRepository.getCollectionOverlapForLibrary("LIB_A"));

		// One row per peer, never a matrix: B and C, and B first because it shares more.
		assertThat(overlaps, hasSize(2));
		assertThat(overlaps.get(0).rightSystemCode(), equalTo("LIB_B"));
		assertThat(overlaps.get(0).sharedTitleCount(), equalTo(2L));
		assertThat(overlaps.get(1).rightSystemCode(), equalTo("LIB_C"));
		assertThat(overlaps.get(1).sharedTitleCount(), equalTo(1L));

		// The caller is always the left side, and never paired with itself - the B/C pair that
		// the full matrix emitted is not this caller's business and doubles the work.
		assertThat(overlaps.stream().map(CollectionOverlapStat::leftSystemCode).distinct().toList(),
			contains("LIB_A"));
	}

	@Test
	void overlapCoversEveryLibraryAMultiLibraryCallerAdministers() {
		// The comma-separated shape every scoped query uses. Somebody running a shared Koha for
		// two of its tenants must see both, each against its own peers.
		final var shared = cluster();
		bib(libA, shared);
		bib(libB, shared);
		bib(libC, shared);

		final var overlaps = manyValuesFrom(
			bibRepository.getCollectionOverlapForLibrary("LIB_A,LIB_B"));

		// A->B, A->C, B->A, B->C. Still no self-pairing.
		assertThat(overlaps, hasSize(4));
		assertThat(overlaps.stream().map(CollectionOverlapStat::leftSystemCode).distinct().sorted().toList(),
			contains("LIB_A", "LIB_B"));
		assertThat(overlaps.stream()
			.noneMatch(o -> o.leftSystemCode().equals(o.rightSystemCode())), equalTo(true));
	}

	@Test
	void clusterSizeDistributionCountsHoldersPerWork() {
		final var singlyHeld = cluster();
		bib(libA, singlyHeld);

		final var alsoSinglyHeld = cluster();
		bib(libB, alsoSinglyHeld);

		final var doublyHeld = cluster();
		bib(libA, doublyHeld);
		bib(libB, doublyHeld);

		final var distribution = manyValuesFrom(bibRepository.getClusterSizeDistribution());

		assertThat(distribution, hasSize(2));

		final ClusterSizeStat ones = distribution.get(0);
		assertThat(ones.holderCount(), equalTo(1));
		assertThat(ones.clusterCount(), equalTo(2L));

		final ClusterSizeStat twos = distribution.get(1);
		assertThat(twos.holderCount(), equalTo(2));
		assertThat(twos.clusterCount(), equalTo(1L));
	}

	@Test
	void formatProfileCountsWorksNotRecords() {
		// A catalogues the same work three times and also holds a second work; B holds the first
		// work once. Counting bib_record rows reads A as four Books, which is the number that
		// would not reconcile against its cluster count of two.
		final var shared = cluster();
		bib(libA, shared);
		bib(libA, shared);
		bib(libA, shared);
		bib(libB, shared);

		final var aOnly = cluster();
		bib(libA, aOnly);

		final var formats = manyValuesFrom(bibRepository.getFormatProfile());

		assertThat(formats, hasSize(2));
		assertThat(formatFor(formats, libA).titleCount(), equalTo(2L));
		assertThat(formatFor(formats, libB).titleCount(), equalTo(1L));

		// Same number the collection profile reports, which is the whole point of the change.
		final var profiles = manyValuesFrom(bibRepository.getCollectionProfile());
		assertThat(profileFor(profiles, libA).clusterCount(), equalTo(2L));
	}

	@Test
	void formatProfileExcludesDeletedClusters() {
		final var liveCluster = cluster();
		bib(libA, liveCluster);

		final var deletedCluster = deletedCluster();
		bib(libA, deletedCluster);

		final var formats = manyValuesFrom(bibRepository.getFormatProfile());

		assertThat(formatFor(formats, libA).titleCount(), equalTo(1L));
	}

	@Test
	void formatProfileReportsWorksWithNoDerivedTypeRatherThanDroppingThem() {
		// derived_type is varchar(32) with no NOT NULL, so an ingest that could not derive one
		// leaves it null. Dropping those rows would make the format mix disagree with the
		// collection profile; a non-null record component would fail to deserialise them.
		final var typed = cluster();
		bib(libA, typed);

		final var untyped = cluster();
		bibWithoutDerivedType(libA, untyped);

		final var formats = manyValuesFrom(bibRepository.getFormatProfile());

		assertThat(formats, hasSize(2));
		assertThat(formats.stream().map(SourceFormatStat::derivedType).toList(),
			containsInAnyOrder("Book", null));
	}

	@Test
	void collectionTotalsCountEachWorkOnceHoweverManySourcesHoldIt() {
		// Held by all three. Summing the per-source cluster_count would report this work three
		// times - that sum is holdings, and conflating the two is the mistake this query exists
		// to make impossible.
		final var everyone = cluster();
		bib(libA, everyone);
		bib(libB, everyone);
		bib(libC, everyone);

		// A second work held by A alone, catalogued twice.
		final var aOnly = cluster();
		bib(libA, aOnly);
		bib(libA, aOnly);

		final var totals = singleValueFrom(bibRepository.getCollectionTotals());

		assertThat(totals.distinctTitles(), equalTo(2L));
		assertThat(totals.singlyHeldTitles(), equalTo(1L));
		// (everyone x 3 sources) + (aOnly x 1 source) - the duplicate bib collapses.
		assertThat(totals.holdings(), equalTo(4L));
		assertThat(totals.contributingSources(), equalTo(3L));
	}

	@Test
	void collectionTotalsIgnoreDeletedClustersAndUnclusteredBibs() {
		final var live = cluster();
		bib(libA, live);

		bib(libA, deletedCluster());

		final var totals = singleValueFrom(bibRepository.getCollectionTotals());

		assertThat(totals.distinctTitles(), equalTo(1L));
		assertThat(totals.holdings(), equalTo(1L));
	}

	@Test
	void overlapIsEmptyWhenNoWorkIsSharedButProfilesStillReport() {
		final var aOnly = cluster();
		bib(libA, aOnly);

		final var bOnly = cluster();
		bib(libB, bOnly);

		assertThat(manyValuesFrom(bibRepository.getCollectionOverlapForLibrary("LIB_A")),
			hasSize(0));

		final var profiles = manyValuesFrom(bibRepository.getCollectionProfile());
		assertThat(profileFor(profiles, libA).uniqueTitleCount(), equalTo(1L));
		assertThat(profileFor(profiles, libB).uniqueTitleCount(), equalTo(1L));
	}
}

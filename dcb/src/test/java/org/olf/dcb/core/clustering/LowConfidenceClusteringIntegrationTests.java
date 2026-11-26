package org.olf.dcb.core.clustering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.audit.ProcessAuditService;
import org.olf.dcb.core.clustering.matching.MatchpointService;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.clustering.model.MatchPoint;
import org.olf.dcb.core.model.BibIdentifier;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.ingest.IngestService;
import org.olf.dcb.storage.BibIdentifierRepository;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.olf.dcb.test.DcbTestContainerContextBuilder;

import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@MicronautTest(transactional = false, startApplication = false,
	contextBuilder = DcbTestContainerContextBuilder.class)
class LowConfidenceClusteringIntegrationTests {
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	@Inject private DefaultRecordClusteringService clustering;
	@Inject private MatchpointService matchpoints;
	@Inject private BibRepository bibs;
	@Inject private BibIdentifierRepository identifiers;
	@Inject private ClusterRecordRepository clusters;
	@Inject private R2dbcReactorTransactionOperations transactions;

	@Test
	void joinsAPeerClusterWhenOnlyLowConfidencePointsMatch() {
		var title = "The missing candidate lookup";
		var isbn = "9780140328721";
		var peer = createBib(title, isbn, newCluster(title));
		var incoming = createBib(title, isbn, null);

		var ownPoints = transactions.withTransactionMono(status ->
			clustering.generateMatchPoints(incoming).collectList()).block(TIMEOUT);
		assertEquals(Set.of("ISBN-N", "BLOCKING_TITLE"), ownPoints.stream()
			.map(MatchPoint::getDomain).collect(Collectors.toSet()));
		assertEquals(Set.of(incoming.getId()), bibIds(ownPoints));
		assertEquals(Set.of(peer.getId()), bibIds(peerHits(incoming, ownPoints)));

		var savedCluster = cluster(incoming);

		assertEquals(peer.getContributesTo().getId(), savedCluster,
			"The normal clustering path must join the peer's cluster using the two low-confidence hits");
	}

	@Test
	void oneLowConfidenceMatchWithoutTitleEvidenceKeepsBibsSeparate() {
		assertSeparateWithoutTitle(List.of("9780374533557"));
	}

	@Test
	void threeLowConfidenceMatchesWithoutTitleEvidenceKeepBibsSeparate() {
		assertSeparateWithoutTitle(List.of("9780062316097", "9780140449136", "9780140449143"));
	}

	private void assertSeparateWithoutTitle(List<String> isbns) {
		var peerTitle = "Quantum mechanics " + isbns.size();
		var peerCluster = newCluster(peerTitle);
		var peer = createBib(peerTitle, isbns, peerCluster);
		var incoming = createBib("Gardening with roses " + isbns.size(), isbns, null);

		var ownPoints = transactions.withTransactionMono(status ->
			clustering.generateMatchPoints(incoming).collectList()).block(TIMEOUT);
		var hits = peerHits(incoming, ownPoints);
		assertEquals(Set.of(peer.getId()), bibIds(hits));
		assertEquals(Set.of("ISBN-N"), hits.stream().map(MatchPoint::getDomain).collect(Collectors.toSet()));
		assertEquals(isbns.size(), hits.stream().map(MatchPoint::getValue).distinct().count());
		assertNotEquals(peerCluster.getId(), cluster(incoming));
	}

	@Test
	void secondaryComparisonSkipsTheAlreadyMatchedClusterAndReturnsAllOtherPeers() {
		var title = "The inverted secondary filter";
		var isbn = "9780307476463";
		var alreadyMatched = newCluster(title);
		var firstUnmatched = newCluster(title);
		var secondUnmatched = newCluster(title);
		var matchedPeer = createBib(title, isbn, alreadyMatched);
		var firstUnmatchedPeer = createBib(title, isbn, firstUnmatched);
		var secondUnmatchedPeer = createBib(title, isbn, secondUnmatched);
		var incoming = createBib(title, isbn, null);

		var ownPoints = transactions.withTransactionMono(status ->
			clustering.generateMatchPoints(incoming).collectList()).block(TIMEOUT);
		var hits = peerHits(incoming, ownPoints);
		for (var peer : List.of(matchedPeer, firstUnmatchedPeer, secondUnmatchedPeer)) {
			assertEquals(Set.of("ISBN-N", "BLOCKING_TITLE"), hits.stream()
				.filter(hit -> hit.getBibId().equals(peer.getId()))
				.map(MatchPoint::getDomain).collect(Collectors.toSet()));
		}

		// Feed real hits directly to exercise exclusion and multi-bib loading independently of the lookup.
		var matches = secondaryMatches(incoming, hits, List.of(alreadyMatched));
		assertEquals(2, matches.size());
		assertEquals(Set.of(firstUnmatched.getId(), secondUnmatched.getId()), Set.copyOf(matches));
	}

	private List<UUID> secondaryMatches(BibRecord incoming, List<MatchPoint> hits, List<ClusterRecord> alreadyMatched) {
		return transactions.withTransactionMono(status -> clustering.doExtraProcessing(incoming, hits, alreadyMatched)
			.transform(ProcessAuditService.withNewProcessAudit("low-match-test",
				context -> context.processSubject(incoming.getId())))
			.map(ClusterRecord::getId).collectList()).block(TIMEOUT);
	}

	private UUID cluster(BibRecord incoming) {
		var savedCluster = transactions.withTransactionMono(status ->
			Mono.from(clustering.clusterBib(incoming)
				.transform(ProcessAuditService.withNewProcessAudit("low-match-test")))
				.map(bib -> bib.getContributesTo().getId())).block(TIMEOUT);
		assertNotNull(savedCluster);
		assertEquals(savedCluster, Mono.from(bibs.findContributesToById(incoming.getId()))
			.map(ClusterRecord::getId).block(TIMEOUT));
		return savedCluster;
	}

	private List<MatchPoint> peerHits(BibRecord incoming, List<MatchPoint> ownPoints) {
		var values = ownPoints.stream().map(MatchPoint::getValue).toList();
		return transactions.withTransactionMono(status ->
			matchpoints.getAllCandidateMatchpointHits(incoming, values).collectList()).block(TIMEOUT);
	}

	private Set<UUID> bibIds(List<MatchPoint> points) {
		return points.stream().map(MatchPoint::getBibId).collect(Collectors.toSet());
	}

	private ClusterRecord newCluster(String title) {
		return Mono.from(clusters.save(ClusterRecord.builder().id(UUID.randomUUID()).title(title).build()))
			.block(TIMEOUT);
	}

	private BibRecord createBib(String title, String isbn, ClusterRecord cluster) {
		return createBib(title, List.of(isbn), cluster);
	}

	private BibRecord createBib(String title, List<String> isbns, ClusterRecord cluster) {
		var id = UUID.randomUUID();
		var bib = BibRecord.builder().id(id).sourceSystemId(UUID.randomUUID()).sourceRecordId(id.toString())
			.title(title).blockingTitle(title).derivedType("BOOK").contributesTo(cluster)
			.processVersion(IngestService.getProcessVersion()).build();
		return transactions.withTransactionMono(status -> Mono.<BibRecord>from(bibs.save(bib))
			.flatMap(saved -> Flux.concat(
				Flux.fromIterable(isbns).concatMap(isbn -> identifiers.save(identifier(saved, "ISBN-N", isbn))),
				identifiers.save(identifier(saved, "BLOCKING_TITLE", title)))
				.then(Mono.just(saved)))
			.flatMap(saved -> clustering.generateMatchPoints(saved).collectList()
				.flatMap(points -> matchpoints.reconcileMatchPoints(points, saved))
				.thenReturn(saved))).block(TIMEOUT);
	}

	private BibIdentifier identifier(BibRecord bib, String namespace, String value) {
		return BibIdentifier.builder().id(UUID.randomUUID()).owner(bib).namespace(namespace).value(value).build();
	}
}

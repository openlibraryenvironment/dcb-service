package org.olf.dcb.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import org.olf.dcb.core.model.BibIdentifier;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.model.clustering.MatchPoint;
import org.olf.dcb.stats.StatsService;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.olf.dcb.storage.MatchPointRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.util.StringUtils;
import io.micronaut.retry.annotation.Retryable;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import services.k_int.utils.Predicates;

@Singleton
public class RecordClusteringService {

	private static final String MATCHPOINT_ID = "id";
	private static final String MATCHPOINT_TITLE = "title";

	private static final Logger log = LoggerFactory.getLogger(RecordClusteringService.class);

	final ClusterRecordRepository clusterRecords;
	final BibRecordService bibRecords;
	final MatchPointRepository matchPointRepository;

	final StatsService statsService;

	public RecordClusteringService(
		ClusterRecordRepository clusterRecordRepository,
		BibRecordService bibRecordService, MatchPointRepository matchPointRepository, StatsService statsService) {
		this.clusterRecords = clusterRecordRepository;
		this.bibRecords = bibRecordService;
		this.matchPointRepository = matchPointRepository;
		this.statsService = statsService;
	}

	// Get cluster record by id
	public Mono<ClusterRecord> findById(UUID id) {
		return Mono.from(clusterRecords.findOneById(id));
	}

	// Add Bib to cluster record
//	public Mono<BibRecord> addBibToClusterRecord(BibRecord bib, ClusterRecord clusterRecord) {
//		return Mono.just(bib.setContributesTo(clusterRecord)).flatMap(bibRecords::saveOrUpdate);
//	}

	// Get all bibs for cluster record id

	public Flux<BibRecord> findBibsByClusterRecord(ClusterRecord clusterRecord) {
		return Flux.from(bibRecords.findAllByContributesTo(clusterRecord));
	}
	
	private boolean completeIdentifiersPredicate ( BibIdentifier bibId ) {
		return Stream.of( bibId.getNamespace(), bibId.getValue())
			.map( StringUtils::trimToNull )
			.count() == 2;
	}
	
	@Transactional
	public Mono<Tuple2<List<MatchPoint>, List<ClusterRecord>>> collectClusterRecords(String derivedType, Publisher<MatchPoint> matchPoints) {
		 return Flux.from( matchPoints )
		 	.collectList()
		 	.flatMap( mps -> {
		 		var ids = mps.stream()
		 			.map( MatchPoint::getValue )
		 			.collect(Collectors.toUnmodifiableSet());
		 		
		 		return Flux.from( clusterRecords.findAllByDerivedTypeAndMatchPoints(derivedType, ids ) )
		 				.collectList()
		 				.map( crs -> Tuples.of( mps, crs ));
		 	});
	}
	
	// Remove the items in a new transaction.
	@Transactional
	public Mono<ClusterRecord> mergeClusterRecords( ClusterRecord to, Collection<ClusterRecord> from ) {
		
		return Mono.fromDirect( bibRecords.moveBetweenClusterRecords(from, to) )
			.then( Mono.fromDirect(
					clusterRecords.deleteByIdInList(
							from
								.stream()
								.map(ClusterRecord::getId)
								.toList())))
			.thenReturn( to );
	}
	
	@Transactional
	public Mono<ClusterRecord> reduceClusterRecords( final int pointsCreated,  final List<ClusterRecord> clusterList ) {
		final int matches = clusterList.size();
		
		return switch (matches) {
			case 0 -> Mono.empty();
			
			case 1 -> {
				
				if (pointsCreated > 2) {
					log.trace("Match point - Match ratio too low. Cannot quick match");
					yield Mono.empty();
				}
				
				// Single match point.
				log.trace("Low number of match points, but found single match.");
				yield Mono.just( clusterList.get(0) );
			}
			
			default -> {
				
				final LinkedHashSet<ClusterRecord> clusters = new LinkedHashSet<>(clusterList);
				yield switch ( clusters.size() ) {
					case 1 -> {
						log.trace("Single cluster matched by multiple match points.");
						yield Mono.just( clusters.iterator().next() );
					}
					default -> {
						log.info("Multiple cluster matched. Use first cluster and merge others");
						
						// Pop the first item.
						var items = clusters.iterator();
						
						Set<ClusterRecord> toRemove = new HashSet<>();
						
						var primary = items.next();
						items.forEachRemaining(c -> {
							if (c.getId() != null && c.getId() != primary.getId()) {
								toRemove.add(c);
							}});
						
						yield mergeClusterRecords(primary, toRemove);
					}
				};
			}
		};
		
	}
	
	@Transactional
	public Mono<ClusterRecord> saveMatchPointsAndMergeClusters(List<MatchPoint> matchPoints, List<ClusterRecord> clusters) {
		return Flux.from( matchPointRepository.saveAll(matchPoints) )
			.then( Mono.just( Tuples.of( matchPoints.size(), clusters ))
					.flatMap( TupleUtils.function( this::reduceClusterRecords )));
	}
	
	@Transactional
	public Flux<MatchPoint> idMatchPoints( BibRecord bib ) {
		return bibRecords.findAllIdentifiersForBib( bib )
				.filter( Predicates.failureLoggingPredicate(
						this::completeIdentifiersPredicate, log::info, "Blank match point skipped for bib: {}", bib.getId()))
				
				.map( id -> String.format("%s:%s:%s", MATCHPOINT_ID, id.getNamespace(), id.getValue()) )
				.map( MatchPoint::buildFromString ) ;
	}

	private Flux<MatchPoint> recordMatchPoints ( BibRecord bib ) {
		
		return Mono.justOrEmpty( bib.getBlockingTitle() )
			.map( bt -> String.format("%s:%s", MATCHPOINT_TITLE, bt) )
			.map( MatchPoint::buildFromString )
			.as(Flux::from);
	}
	
	private Flux<MatchPoint> collectMatchPoints ( final BibRecord bib ) {
		return Flux.concat(
				this.idMatchPoints(bib),
				this.recordMatchPoints(bib))
					.map( mp -> mp.toBuilder()
						.bibId( bib.getId() )
						.build());
	}
	
	@Transactional
	public Mono<ClusterRecord> createMinimalCluster( BibRecord bib ) {
		var cluster = ClusterRecord.builder()
				.id(UUID.randomUUID())
				.title( bib.getTitle() )
				.selectedBib( bib.getId() )
				.build();
		
		return Mono.just ( cluster )
			.map( clusterRecords::save )
			.flatMap( Mono::fromDirect );
	}

	@Retryable
	@Transactional(value = TxType.REQUIRES_NEW)
	public Mono<BibRecord> clusterBib ( final BibRecord bib ) {

		// Generate MatchPoints
		return Mono.justOrEmpty( bib )
			.flatMap( theBib -> Mono.fromDirect( matchPointRepository.deleteAllByBibId( theBib.getId()))
					.thenReturn( theBib ) )
			.map( this::collectMatchPoints )
			.flatMap( matchPointPub -> collectClusterRecords(bib.getDerivedType(), matchPointPub) )
			.flatMap(TupleUtils.function( this::saveMatchPointsAndMergeClusters ))
			.switchIfEmpty( createMinimalCluster(bib) )
			.flatMap( cr -> {
				final var linkedBib = bib.toBuilder()
					.contributesTo(cr)
					.build();

				return Mono.fromDirect( bibRecords.update(linkedBib) );
			});
		
		// Find all clusters matching MatchPoints (via bibs)
		// Take lastUpdated Cluster record as match
		// -> Asynchronously merge other Cluster Records
		// Add matchkeys, and cluster to bib.
		// Save and return bib.
	}
}

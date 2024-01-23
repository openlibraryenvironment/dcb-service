package org.olf.dcb.core.svc;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import io.micrometer.core.annotation.Timed;

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
			BibRecordService bibRecordService,
			MatchPointRepository matchPointRepository,
		  StatsService statsService) {
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
		
		boolean value = Stream.of( bibId.getNamespace(), bibId.getValue())
			.map( StringUtils::trimToNull )
			.filter( Objects::nonNull )
			.count() == 2;
		

		if ( !value ) {
			log.atInfo()
				.log("Blank match point skipped for bib: {}", bibId.getOwner().getId());
		}

		return value;
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
	
	@Transactional
	public Mono<Void> softDelete(ClusterRecord theRecord) {
		log.debug("Soft delete {}",theRecord.getId());
		return Mono.justOrEmpty(theRecord)
			.filter( cr -> Objects.nonNull(cr.getId()) )
			.map( current -> 
				// Create a new record for the deleted item.
				ClusterRecord.builder()
					.id(current.getId())
					.dateCreated(current.getDateCreated())
					.isDeleted(true)
					.build())
			.map( this::update )
			.flatMap(Mono::from)
			.doOnNext(cr -> log.debug("Soft deleted cluster {}", cr.getId()))
			.then();
	}
	
	@Transactional
	public Mono<Void> softDeleteByIdInList(Collection<UUID> ids) {
		return findAllByIdInList(ids)
			.flatMap( this::softDelete )
			.then();
	}
	
	@Transactional
	public Flux<ClusterRecord> findAllByIdInList(Collection<UUID> ids) {
		return Flux.from(clusterRecords.findAllByIdInList(ids));
	}
	
	@Transactional
	public Mono<Page<UUID>> findNext1000UpdatedBefore(@NonNull Instant before, Pageable page) {
		return Mono.from(clusterRecords.findIdByDateUpdatedLessThanEqualsOrderByDateUpdated(before, page));
	}
	
	@Transactional
	public Flux<ClusterRecord> findAllByIdInListWithBibs(Collection<UUID> ids) {
		return Flux.from(clusterRecords.findByIdInListWithBibs(ids));
	}
	
	// Remove the items in a new transaction.
	@Transactional
	protected Mono<ClusterRecord> mergeClusterRecords( ClusterRecord to, Collection<ClusterRecord> from ) {
		
		return Mono.fromDirect( bibRecords.moveBetweenClusterRecords(from, to) )
			.then( Mono.fromDirect(
					this.softDeleteByIdInList(
							from
								.stream()
								.map(ClusterRecord::getId)
								.toList())))
			.thenReturn( to );
	}
	
	@Transactional
	protected Mono<ClusterRecord> reduceClusterRecords( final int pointsCreated,  final List<ClusterRecord> clusterList ) {
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
						log.trace("Multiple cluster matched. Use first cluster and merge others");
						
						// Pop the first item.
						var items = clusters.iterator();
						
						Set<ClusterRecord> toRemove = new HashSet<>();
						
						var primary = items.next();
						items.forEachRemaining(c -> {
							if (c.getId() != null && c.getId() != primary.getId()) {
								toRemove.add(c);
							}});
						
						yield mergeClusterRecords(primary, toRemove)
							.flatMap(this::electSelectedBib);
					}
				};
			}
		};
		
	}
	
	@Timed("bib.cluster.matchAndMerge")
	@Transactional
	protected Mono<ClusterRecord> saveMatchPointsAndMergeClusters(List<MatchPoint> matchPoints, List<ClusterRecord> clusters) {
		return Flux.from( matchPointRepository.saveAll(matchPoints) )
			.then( Mono.just( Tuples.of( matchPoints.size(), clusters ))
					.flatMap( TupleUtils.function( this::reduceClusterRecords )));
	}
	
	@Transactional
	public Flux<MatchPoint> idMatchPoints( BibRecord bib ) {
		return bibRecords.findAllIdentifiersForBib( bib )
				.filter( this::completeIdentifiersPredicate )
				
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
	
	@Timed("bib.cluster.minimal")
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
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> touch ( final ClusterRecord cluster ) {
		return Mono.justOrEmpty(cluster.getId())
			.flatMap( theId -> {
				return Mono.from(clusterRecords.touch(theId) )
					.doOnNext( total -> {
						log.debug("Touch updatedDate on cluster record {} yeilded {} records updated", theId, total);
					});
			})
			.thenReturn( cluster );
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> update ( final ClusterRecord cluster ) {
		return Mono.from ( clusterRecords.update(cluster) ).thenReturn(cluster);
	}

	@Timed("bib.cluster")
	@Retryable
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Mono<BibRecord> clusterBib ( final BibRecord bib ) {

		// Generate MatchPoints
		return Mono.justOrEmpty( bib )
			.flatMap( theBib -> Mono.fromDirect( matchPointRepository.deleteAllByBibId( theBib.getId() ))
					.thenReturn( theBib ) )
			.map( this::collectMatchPoints )
			.flatMap( matchPointPub -> collectClusterRecords(bib.getDerivedType(), matchPointPub) )
			.flatMap(TupleUtils.function( this::saveMatchPointsAndMergeClusters ))
			.flatMap( this::touch ) // Ensure the matched ClusterRecord's date is changed.
			
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
	
	
	public <T> Mono<Page<T>> getPageAs(Optional<Instant> since, Pageable pageable, Function<ClusterRecord, T> mapper) {

		// return Mono.from( _clusterRecordRepository.findAll(pageable) )
		return Mono.from(
			clusterRecords.findByDateUpdatedGreaterThanOrderByDateUpdated(
				since.orElse(Instant.ofEpochMilli(0L)),
				pageable))
				
			.map(page -> page.map(mapper));
	}
	

	@Timed("bib.cluster.elect")
	@Transactional
	public Mono<ClusterRecord> electSelectedBib( final ClusterRecord cr ) {
		return this.electSelectedBib(cr, Optional.empty());
	}
	
	@Transactional
	public Mono<ClusterRecord> electSelectedBib( final ClusterRecord cr, final Optional<BibRecord> ignoreBib ) {
		// Use the record with the highest score
		return bibRecords.findTop2HighestScoringContributorId( cr )
			.filter( id -> 
				ignoreBib
					.map( ignore -> !id.toString().equals(ignore.getId().toString()) )
					.orElse(Boolean.TRUE))
			
			.next()
			.zipWith(Mono.just(cr))
			.map(TupleUtils.function((contrib, cluster) -> {
				log.debug("Setting selected bib on cluster record {} to {}", cluster.getId(), contrib);
				return cluster.setSelectedBib(contrib);
			}))
			.flatMap(this::update)
			.defaultIfEmpty(cr);
	}
}

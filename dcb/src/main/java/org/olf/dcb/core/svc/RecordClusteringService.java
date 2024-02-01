package org.olf.dcb.core.svc;

import static services.k_int.utils.TupleUtils.curry;

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
import java.util.stream.Stream;

import org.olf.dcb.core.model.BibIdentifier;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.model.clustering.MatchPoint;
import org.olf.dcb.stats.StatsService;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.olf.dcb.storage.MatchPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.annotation.Timed;
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
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> findById(UUID id) {
		return Mono.from(clusterRecords.findById(id));
	}	

	// Add Bib to cluster record
//	public Mono<BibRecord> addBibToClusterRecord(BibRecord bib, ClusterRecord clusterRecord) {
//		return Mono.just(bib.setContributesTo(clusterRecord)).flatMap(bibRecords::saveOrUpdate);
//	}

	// Get all bibs for cluster record id

//	@Transactional(propagation = Propagation.REQUIRED)
//	public Flux<BibRecord> findBibsByClusterRecord(ClusterRecord clusterRecord) {
//		return Flux.from(bibRecords.findAllByContributesTo(clusterRecord));
//	}
	
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
	

	@Transactional(propagation = Propagation.MANDATORY)
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
			.flatMap( this::saveOrUpdate )
			.doOnNext(cr -> log.debug("Soft deleted cluster {}", cr.getId()))
			.then();
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Void> softDeleteByIdInList(Collection<UUID> ids) {
		return findAllByIdInList(ids)
			.flatMap( this::softDelete )
			.then();
	}
	

	@Transactional(propagation = Propagation.MANDATORY)
	public Flux<ClusterRecord> findAllByIdInList(Collection<UUID> ids) {
		return Flux.from(clusterRecords.findAllByIdInList(ids));
	}
	

	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Page<UUID>> findNext1000UpdatedBefore(@NonNull Instant before, Pageable page) {
		return Mono.from(clusterRecords.findIdByDateUpdatedLessThanEqualsOrderByDateUpdated(before, page));
	}
	

	@Transactional(propagation = Propagation.MANDATORY)
	public Flux<ClusterRecord> findAllByIdInListWithBibs(Collection<UUID> ids) {
		return Flux.from(clusterRecords.findByIdInListWithBibs(ids));
	}
	
	// Remove the items in a new transaction.
	@Timed("bib.cluster.merge")
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<ClusterRecord> mergeClusterRecords( ClusterRecord to, Collection<ClusterRecord> from ) {
		
		return Mono.fromDirect( bibRecords.moveBetweenClusterRecords(from, to) )
			.flatMap( cr -> softDeleteByIdInList(
					from
						.stream()
						.map(ClusterRecord::getId)
						.toList())
					.thenReturn( cr ));
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<ClusterRecord> reduceClusterRecords( final int pointsCreated,  final List<ClusterRecord> clusterList ) {
		final int matches = clusterList.size();
		
		return switch (matches) {
			case 0 -> Mono.empty();
			
			case 1 -> {
				
				// SO: For now lets not do this...
//				if (pointsCreated > 2) {
//					log.trace("Match point - Match ratio too low. Cannot quick match");
//					yield Mono.empty();
//				}
				
				// Single match point.
				ClusterRecord cr = clusterList.get(0);
				log.trace("Low number of match points, but found single match. {}/{}", cr.getId(),cr.getTitle());
				yield Mono.just( cr );
			}
			
			default -> {
				
				// Sort the unique entries.
				final List<ClusterRecord> clusters = new LinkedHashSet<>(clusterList).stream()
					.sorted((cr1, cr2) -> cr2.getDateUpdated().compareTo(cr1.getDateUpdated()))
					.toList()
				;
				
				yield switch ( clusters.size() ) {
					case 1 -> {
						ClusterRecord cr = clusters.iterator().next();
						log.trace("Single cluster matched by multiple match points. {}/{}",cr.getId(), cr.getTitle());
						yield Mono.just( cr );
					}
					default -> {
						
						// Pop the first item.
						var items = clusters.iterator();
						
						Set<ClusterRecord> toRemove = new HashSet<>();
						
						var primary = items.next();

						log.trace("Multiple cluster matched. Use first cluster and merge others {}/{}",primary.getId(), primary.getTitle());

						items.forEachRemaining(c -> {
							if (c.getId() != null && !c.getId().toString().equals(Objects.toString(primary.getId(), null))) {
								toRemove.add(c);
							}});
						
						yield mergeClusterRecords(primary, toRemove);
//							.flatMap(this::electSelectedBib);
					}
				};
			}
		};
		
	}
	
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Flux<MatchPoint> generateIdMatchPoints( BibRecord bib ) {
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
	
	private Flux<MatchPoint> generateMatchPoints ( final BibRecord bib ) {
		log.debug("collectMatchPoints for bib");
		return Flux.concat(
				generateIdMatchPoints(bib),
				recordMatchPoints(bib))
					.map( mp -> mp.toBuilder()
						.bibId( bib.getId() )
						.build());
	}
	
//	@Transactional(propagation = Propagation.MANDATORY)
//	public Mono<ClusterRecord> touch ( final ClusterRecord cluster ) {
//
//		log.debug("request touch cr {}",cluster.getId());
//
//		return Mono.justOrEmpty(cluster.getId())
//			.flatMap( theId -> {
//				return Mono.from(clusterRecords.touch(theId) )
//					.doOnNext( total -> {
//						log.debug("Touch updatedDate on cluster record {} yeilded {} records updated", theId, total);
//					});
//			})
//			.thenReturn( cluster );
//	}
	
//	@Transactional(propagation = Propagation.MANDATORY)
//	public Mono<ClusterRecord> update ( final ClusterRecord cluster ) {
//		return Mono.from ( clusterRecords.update(cluster) );
//	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> saveOrUpdate ( final ClusterRecord cluster ) {
		return Mono.from ( clusterRecords.saveOrUpdate(cluster) )
				.doOnError(t -> log.error("Error adding {}", cluster) );
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<List<MatchPoint>> reconcileMatchPoints( Collection<MatchPoint> currentMatchPoints, BibRecord bib ) {
		
		return Flux.fromIterable( currentMatchPoints )
			.map( MatchPoint::getValue )
			.collectList()
			.map( curry(bib.getId(), matchPointRepository::deleteAllByBibIdAndValueNotIn) )
			.flatMap(Mono::from)
			.doOnNext( del -> log.debug("Deleted {} existing matchpoints that are no longer valid")  )
			.thenMany( Mono.just(currentMatchPoints).flatMapMany( matchPointRepository::saveAll ) )
			.collectList();
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Tuple2<BibRecord, ClusterRecord>> updateBibAndClusterData ( BibRecord bib, Collection<MatchPoint> currentMatchPoints, ClusterRecord cluster ) {
		
		// Save the cluster
		return Mono.just( cluster )
			.flatMap(this::saveOrUpdate)
			// Update the contribution and save the bib
			.zipWhen( cr -> Mono.just(bib.setContributesTo(cr) )
				.flatMap(bibRecords::saveOrUpdate) ) 
			
			// We can do some things at the same time.
			.flatMap(TupleUtils.function((cr, br) ->
				// Reconcile the matchpoints and elect the primary bib on the matched cluster
				Mono.zip(reconcileMatchPoints(currentMatchPoints, br), electSelectedBib( cr ))
					.thenReturn(Tuples.of(br, cr))));
	}
	
	private ClusterRecord newClusterRecord( BibRecord bib ) {
		return ClusterRecord.builder()
				.id( UUID.randomUUID() )
				.title( bib.getTitle() )
				.selectedBib( bib.getId() )
				.build();
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Tuple2<BibRecord, ClusterRecord>> clusterUsingMatchPoints( BibRecord bib, Collection<MatchPoint> matchPoints ) {
		
		return matchClusters(bib, matchPoints)
			.collectList()
			.flatMap( curry( matchPoints.size(), this::reduceClusterRecords ))
			.switchIfEmpty(Mono.just(bib).map(this::newOrExistingClusterRecord))
			.flatMap(curry(bib, matchPoints, this::updateBibAndClusterData));
	}

	private ClusterRecord newOrExistingClusterRecord( BibRecord bib ) {
		if ( bib.getContributesTo() != null ) {
			log.warn("Asked to create a new cluster record because no other clusters matched, but the record already points at a cluster");
		}
		return newClusterRecord(bib);
	}
	
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Flux<ClusterRecord> matchClusters( BibRecord bib, Collection<MatchPoint> matchPoints ) {
		return Flux.fromIterable( matchPoints )
		 	.map(MatchPoint::getValue )
		 	.distinct()
		 	.collectList()
		 	.flux()
		 	.flatMap(ids -> clusterRecords.findAllByDerivedTypeAndMatchPointsNotBelongingToBib(bib.getDerivedType(), ids, bib.getId()));
	}
	
	@Timed("bib.cluster")
	@Transactional(propagation = Propagation.NESTED)
	public Mono<BibRecord> clusterBib ( final BibRecord bib ) {

		return generateMatchPoints( bib )
			.collectList()
			.flatMap( curry(bib, this::clusterUsingMatchPoints ))
			.map(TupleUtils.function( (savedBib, savedCluster) -> {
				log.trace("Cluster {} selected for bib {}", savedCluster, savedBib);
				return savedBib;
			}));
	}
	

	@Transactional(propagation = Propagation.MANDATORY)
	public <T> Mono<Page<T>> getPageAs(Optional<Instant> since, Pageable pageable, Function<ClusterRecord, T> mapper) {

		// return Mono.from( _clusterRecordRepository.findAll(pageable) )
		return Mono.from(
			clusterRecords.findByDateUpdatedGreaterThanOrderByDateUpdated(
				since.orElse(Instant.ofEpochMilli(0L)),
				pageable))
				
			.map(page -> page.map(mapper));
	}
	

	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> electSelectedBib( final ClusterRecord cr ) {
		return electSelectedBib(cr, Optional.empty());
	}

	@Timed("bib.cluster.elect")
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> electSelectedBib( final ClusterRecord cr, final Optional<BibRecord> ignoreBib ) {
		// Use the record with the highest score
		return bibRecords.findTop2HighestScoringContributorId( cr )
			.filter( id -> 
				ignoreBib
					.map( ignore -> !id.toString().equals(ignore.getId().toString()) )
					.orElse(Boolean.TRUE))
			
			.next()
			.map(contrib -> {
				log.debug("Setting selected bib on cluster record {} to {}", cr.getId(), contrib);
				return cr.setSelectedBib(contrib);
			})
			.defaultIfEmpty(cr)
			.flatMap(this::saveOrUpdate);
	}
}

package org.olf.dcb.core.svc;

import static services.k_int.utils.TupleUtils.curry;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.dcb.core.model.BibIdentifier;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.model.clustering.MatchPoint;
import org.olf.dcb.indexing.SharedIndexService;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.olf.dcb.storage.MatchPointRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.annotation.Timed;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Singleton
public class RecordClusteringService {

	private static final String MATCHPOINT_ID = "id";
	private static final String MATCHPOINT_TITLE = "title";

	private static final Logger log = LoggerFactory.getLogger(RecordClusteringService.class);

	final ClusterRecordRepository clusterRecords;
	final BeanProvider<SharedIndexService> sharedIndexService;
	final BibRecordService bibRecords;
	final MatchPointRepository matchPointRepository;
	private final R2dbcOperations operations;


	public RecordClusteringService(
			ClusterRecordRepository clusterRecordRepository,
			BibRecordService bibRecordService,
			MatchPointRepository matchPointRepository, R2dbcOperations operations, BeanProvider<SharedIndexService> sharedIndexService) {
		this.clusterRecords = clusterRecordRepository;
		this.sharedIndexService = sharedIndexService;
		this.bibRecords = bibRecordService;
		this.matchPointRepository = matchPointRepository;
		this.operations = operations;
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
	public Mono<ClusterRecord> softDelete(ClusterRecord theRecord) {
		log.debug("Soft delete {}",theRecord.getId());
		return Mono.justOrEmpty(theRecord)
			.filter( cr -> Objects.nonNull(cr.getId()) )
			.flatMap( current -> 
				// Create a new record for the deleted item.
				Mono.just(ClusterRecord.builder()
					.id(current.getId())
					.dateCreated(current.getDateCreated())
					.isDeleted(true)
					.build())
			
					.flatMap( deleted -> this.saveOrUpdate(deleted)
							.thenReturn( deleted ))
					.doOnNext(cr -> log.debug("Soft deleted cluster {}", cr.getId())));
	}
	
	final Map<ReactiveTransactionStatus<?>, ConcurrentLinkedQueue<Runnable>> events = new ConcurrentHashMap<>();
	
	final Map<ReactiveTransactionStatus<?>, Mono<Void>> trxWatchers = new ConcurrentHashMap<>();
	
	@Transactional(propagation = Propagation.MANDATORY)
	@ExecuteOn(TaskExecutors.BLOCKING)
	protected Mono<Void> onCommittal( Runnable runnable ) {	
		
		return Mono.from(operations.withTransaction( status -> {
			var queue = events.computeIfAbsent(status, (s) -> {
				log.debug("Creating list of events for transaction {}", s.toString());
			  return new ConcurrentLinkedQueue<>();
			});
			queue.add(runnable);
			
			trxWatchers.computeIfAbsent(status, sts -> { 
				var watcher = Mono.just(sts)
					.publishOn(Schedulers.boundedElastic())
					.subscribeOn(Schedulers.boundedElastic())
					.repeat()
					.skipUntil( s ->
					  s.isCompleted()
					)
					.next()
					.map( s -> {
						if (!s.isRollbackOnly()) {
							log.debug("Transaction [{}] committal. Running events.", s.toString());
							events.get(s).forEach(Runnable::run);
						} else {
							log.info("Transaction [{}] rollback, skipping events.", s.toString());
						}
						return s;
					})
					.then()
					.doFinally( _signal -> {
						log.debug("Clean up transactional event queues for [{}]", sts);
						trxWatchers.remove(sts);
						events.remove(sts);
					});
				
				watcher.subscribe( _v -> {}, err -> log.error("Error watching for index update on committal", err) );
				
				return watcher;
			});
			
			return Mono.empty();
		}));
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
	protected Mono<ClusterRecord> reduceClusterRecords( final int pointsCreated, final List<ClusterRecord> matchedClusterList, Optional<ClusterRecord> currentCluster ) {
		
		// Create a map with the id of the cluster against the number of times it matched.
		return Flux.fromIterable(matchedClusterList)
			.collectMultimap(cr -> cr.getId().toString())
			.map( ocurrences -> {
				
				if (( log.isDebugEnabled() && ocurrences.size() > 1 ) || log.isTraceEnabled()) {
					ocurrences.forEach( (id, occs) -> log.debug("Matched Cluster [{}], on [{}] match points", id, occs.size()) );
				}
				
				// Now take the entrySet and turn into an ordered list where the top result is
				// The highest matched on record
				final List<ClusterRecord> sortedOccurences = ocurrences.values().stream()
					.sorted( (val1, val2) -> {

						int val1Size = val1.size();
						int val2Size = val2.size();
						
						int comp = Integer.compare(val2Size, val1Size);
						if ( comp == 0 && (val1Size + val2Size) > 0) { 
							var currentId = currentCluster.map(current -> current.getId().toString()).orElse(null);
							
							if (val1.iterator().next().getId().toString().equals(currentId)) {
								log.trace("Current cluster used as equal weight tie-breaker.");
								return -1;
							}
							
							if (val2.iterator().next().getId().toString().equals(currentId)) {
								log.trace("Current cluster used as equal weight tie-breaker.");
								return 1;
							}
						}
						
						return comp;
					})
					.map( coll -> coll.iterator().next() )
					.collect(Collectors.toList());
				
				// The list contains the sorted (weighted) matches via the match points. We should add the current
				// cluster as the lowest weighted cluster, if it wasn't matched by the points. This is likely because the
				// previously seen bib was not matched, because the data had changed so much. If we don't add this cluster,
				// it would be left in the database in circumstances, as an orphan.
				currentCluster
					.filter( current -> !ocurrences.containsKey(current.getId().toString()) )
					.ifPresent( sortedOccurences::add );
				
				return sortedOccurences;
			})
			.flatMap( prioritisedClusters -> {
				
				if (prioritisedClusters.size() == 0) {
					log.trace("Didn't match any clusters in the databse and no current cluster. Requires new.");
					return Mono.empty();
				}
				
				// First item is our cluster match
				var primary = prioritisedClusters.remove(0);
				if (prioritisedClusters.size() == 0) {
					
					if (log.isTraceEnabled() && currentCluster.isPresent()) {
						log.trace("Matched [{}], which is existing cluster");
					} else {
						log.trace("Matched [{}]", primary.getId());
					}
					
					return Mono.just(primary);
				}
				
				if (log.isDebugEnabled()) {
					log.debug("Matched [{}], and need to absorb [{}]", primary.getId(), prioritisedClusters.stream().map(cr -> cr.getId().toString()).collect(Collectors.joining(" ,")));
				}
				return mergeClusterRecords(primary, prioritisedClusters);
				
			});		
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
		log.trace("collectMatchPoints for bib");
		return Flux.concat(
				generateIdMatchPoints(bib),
				recordMatchPoints(bib))
					.map( mp -> mp.toBuilder()
						.bibId( bib.getId() )
						.build());
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> saveOrUpdate ( final ClusterRecord cluster ) {
		
		return Mono.just(cluster)
			.zipWhen( cr -> Mono.from( clusterRecords.existsById( cr.getId() )))
			.flatMap( TupleUtils.function((cr, update) -> {
				final Function<ClusterRecord, Publisher<? extends ClusterRecord>> saveMethod = update ? clusterRecords::update : clusterRecords::save;
				
				final Mono<ClusterRecord> saveChain = Mono.just(cr)
					.map( saveMethod )
					.flatMap(Mono::from);
				
				if (!sharedIndexService.isPresent()) {
					return saveChain;
				}
				
				return saveChain
					.flatMap(c -> 
						onCommittal(() -> {
							final UUID id = c.getId();
							
							if (Boolean.TRUE.equals(c.getIsDeleted())) {
								log.trace("Delete index for record [{}]", id);
								sharedIndexService.get().delete(cr.getId());
								return;
							}
							
							if (update) {
								log.trace("Update index for record [{}]", id);
								sharedIndexService.get().update( id );
								return;
							}
							
							// Addition
							log.trace("Add index for record [{}]", id);
							sharedIndexService.get().add( c.getId() );
						})
						.thenReturn(c));
			}));
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Collection<MatchPoint>> reconcileMatchPoints( Collection<MatchPoint> currentMatchPoints, BibRecord bib ) {
		
		return Flux.fromIterable( currentMatchPoints )
			.map( MatchPoint::getValue )
			.collectList()
			.map( curry(bib.getId(), matchPointRepository::deleteAllByBibIdAndValueNotIn) )
			.flatMap(Mono::from)
			.doOnNext( del -> {
				if (del > 0) log.info("Deleted {} existing matchpoints that are no longer valid", del);
			})
			.then( differentMatchPoints(bib.getId(), currentMatchPoints) )
			.flatMapMany( matchPointRepository::saveAll )
			.count()
			.map( added -> {
				if (added > 0) log.info("Added {} new matchpoints", added);
				return currentMatchPoints;
			});
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<List<MatchPoint>> differentMatchPoints ( UUID bibId, Collection<MatchPoint> currentMatchPoints ) {
		return Flux.from( matchPointRepository.findAllByBibId( bibId ) )
			.map( MatchPoint::getValue )
			.collectList()
			.map( exclude -> currentMatchPoints.stream()
					.filter( mp -> !exclude.contains( mp.getValue() ))
					.toList());
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
			.zipWith( bibRecords.getClusterRecordForBib( bib.getId() ).singleOptional() )
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
		 	.map( MatchPoint::getValue )
		 	.distinct()
		 	.collectList()
		 	.flux()
		 	.flatMap(ids -> clusterRecords.findAllByDerivedTypeAndMatchPoints(bib.getDerivedType(), ids));
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
				log.trace("Setting selected bib on cluster record {} to {}", cr.getId(), contrib);
				return cr.setSelectedBib(contrib);
			})
			.defaultIfEmpty(cr)
			.flatMap(this::saveOrUpdate);
	}
}

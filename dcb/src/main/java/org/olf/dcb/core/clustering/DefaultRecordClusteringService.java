package org.olf.dcb.core.clustering;

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

import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.clustering.model.MatchPoint;
import org.olf.dcb.core.model.BibIdentifier;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.dataimport.job.SourceRecordService;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.indexing.SharedIndexService;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.olf.dcb.storage.MatchPointRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.annotation.Timed;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.env.Environment;
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
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Singleton
public class DefaultRecordClusteringService implements RecordClusteringService {

	private static final String MATCHPOINT_ID = "id";
//	private static final String MATCHPOINT_TITLE = "title";

	private static final Logger log = LoggerFactory.getLogger(DefaultRecordClusteringService.class);

	final ClusterRecordRepository clusterRecords;
	final Optional<SourceRecordService> sourceRecordService;
	final BeanProvider<SharedIndexService> sharedIndexService;
	final BibRecordService bibRecords;
	final MatchPointRepository matchPointRepository;
	final Environment environment;
	private final R2dbcOperations operations;


	public DefaultRecordClusteringService(
			ClusterRecordRepository clusterRecordRepository,
			BibRecordService bibRecordService,
			Environment environment,
			MatchPointRepository matchPointRepository, 
			R2dbcOperations operations,
			Optional<SourceRecordService> sourceRecordService,
			BeanProvider<SharedIndexService> sharedIndexService) {
		this.clusterRecords = clusterRecordRepository;
		this.sourceRecordService = sourceRecordService;
		this.sharedIndexService = sharedIndexService;
		this.bibRecords = bibRecordService;
		this.environment = environment;
		this.matchPointRepository = matchPointRepository;
		this.operations = operations;
	}

	// Get cluster record by id
	@Override
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

	// private static final List usefulClusteringIdentifiers = List.of("BLOCKING_TITLE","GOLDRUSH","ISBN-N", "ISSN-N", "ISBN", "ISSN", "LCCN", "OCOLC", "STRN" );
	// ONLY-ISBN-13 is a type indicating that this ISBN was the ONLY one in the record, and therefore should be safe to use
	private static final List<String> usefulClusteringIdentifiers = List.of("BLOCKING_TITLE","GOLDRUSH","ONLY-ISBN-13", "ISSN-N", "LCCN", "OCOLC", "STRN" );

	// Whilst a bib can have many identifiers, some are "Local" and not useful to the clustering process here
	// we restrict the identifiers used for clustering to keep the match set as small as possible.
	private boolean usableForClusteringIdentifiersPredicate ( BibIdentifier bibId ) {

		boolean include = usefulClusteringIdentifiers.contains( bibId.getNamespace().toUpperCase() );

		if ( ( bibId.getConfidence() != null ) && ( bibId.getConfidence().intValue() > 0 ) )
			include = false;

		// if ( !include )
		// 	log.atInfo().log("Local identifier discarded for clustering: {}:{} - confidence={}",bibId.getNamespace(),bibId.getValue(),bibId.getConfidence());
    // else
		// 	log.atInfo().log("Local identifier included for clustering: {}:{} - confidence={}",bibId.getNamespace(),bibId.getValue(),bibId.getConfidence());

		return include;
	}

	

	@Override
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
	
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Void> softDeleteByIdInList(Collection<UUID> ids) {
		return findAllByIdInList(ids)
			.flatMap( this::softDelete )
			.then();
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Flux<ClusterRecord> findAllByIdInList(Collection<UUID> ids) {
		return Flux.from(clusterRecords.findAllByIdInList(ids));
	}
	

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Page<UUID>> findNext1000UpdatedBefore(@NonNull Instant before, Pageable page) {
		return Mono.from(clusterRecords.findIdByDateUpdatedLessThanEqualsOrderByDateUpdated(before, page));
	}
	

	@Override
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
	protected Flux<MatchPoint> generateIdMatchPoints( BibRecord bib ) {
		return bibRecords.findAllIdentifiersForBib( bib )
				.filter( this::completeIdentifiersPredicate )
				.filter( this::usableForClusteringIdentifiersPredicate )
				.map( id -> {
					String s = String.format("%s:%s:%s", MATCHPOINT_ID, id.getNamespace(), id.getValue());
					MatchPoint mp = MatchPoint.buildFromString(s, id.getNamespace());
					return mp;
        } );
	}

  /*
   * Add any non identifier fingerprints we wish to use here
   */
	private Flux<MatchPoint> recordMatchPoints ( BibRecord bib ) {
		
    /*
     * Blocking title is already added by the Marc record handling as an identifier - don't add it again
		return Mono.justOrEmpty( bib.getBlockingTitle() )
			.map( blocking_title -> {
				String s = String.format("%s:%s", MATCHPOINT_TITLE, blocking_title);
				MatchPoint mp = MatchPoint.buildFromString(s, "BT", isDevelopment);
        return mp;
			})
			.as(Flux::from);
      */
    return Flux.empty();
	}
	
	public Flux<MatchPoint> generateMatchPoints ( final BibRecord bib ) {
		return Flux.concat(
				generateIdMatchPoints(bib),
				recordMatchPoints(bib))
					.map( mp -> mp.setBibId(bib.getId()));
	}
	
	@Override
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

    // log.info("reconcileMatchPoints {}",currentMatchPoints);
		
		return Flux.fromIterable( currentMatchPoints )
			.map( MatchPoint::getValue )
			.collectList()
			.map( curry(bib.getId(), matchPointRepository::deleteAllByBibIdAndValueNotIn) )
			.flatMap(Mono::from)
			.doOnNext( del -> {
				if (del > 0) log.info("Deleted {} existing matchpoints that are no longer valid from {}", del, bib.getId());
			})
			.then( differentMatchPoints(bib.getId(), currentMatchPoints) )
			.flatMapMany( matchPointRepository::saveAll )
			.count()
			.map( added -> {
				// if (added > 0) log.trace("Added {} new matchpoints for {}", added, bib.getId());
				return currentMatchPoints;
			});
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<List<MatchPoint>> differentMatchPoints ( UUID bibId, Collection<MatchPoint> currentMatchPoints ) {
		return Flux.from( matchPointRepository.findAllByBibId( bibId ) )
			.map( MatchPoint::getValue )
			.collectList()
			.map( current_bib_match_points -> currentMatchPoints.stream()
          // Include and value not already present
					.filter( mp -> !current_bib_match_points.contains( mp.getValue() ) )
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

		// In prepraration for changing the rules so that a cluster can contain only 1 ONLY-ISBN-13 value
    log.trace("match clusters : {}",matchPoints);

    if ( matchPoints.size() == 0 )
      log.error("0 match points from {}",bib);

		return Flux.fromIterable( matchPoints )
		 	.map( MatchPoint::getValue )
		 	.distinct()
		 	.collectList()
		 	.flux()
		 	.flatMap(ids -> clusterRecords.findAllByDerivedTypeAndMatchPoints(bib.getDerivedType(), ids));
	}

//	// Look into the list of match points to see if we have a ONLY-ISBN-13 identifier ( there should be at most 1 )
//	private String findMP(Collection<MatchPoint> matchPoints, String v ) {
//		return matchPoints.stream()
//			.filter(mp -> v.equals(mp.getDomain()))
//			.map(MatchPoint::getSourceValue)
//			.findFirst()
//			.orElse(null);
//	}
	
	@Override
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
	

	@Override
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
	protected Mono<ClusterRecord> electSelectedBib( final ClusterRecord cr ) {
		return electSelectedBib(cr, Optional.empty());
	}

	@Override
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

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Mono<UUID> disperseAndRecluster (@NotNull @NonNull UUID clusterID) {
		
		// Cluster record
		Mono<ClusterRecord> manifestClusterRecord = findById(clusterID)
				.cache();
		
		// Cluster bibs
		Mono<List<BibRecord>> theBibs = Mono.defer(() -> manifestClusterRecord)
			.flatMapMany( bibRecords::findAllByContributesTo )
			.collectList()
			.cache();
		
		// Get the selected bib, or the first from all bibs if no selected item.
		// This will let us keep the existing cluster.
		Mono<String> findBibToPreserve = manifestClusterRecord
			.flatMap( cluster -> Mono.justOrEmpty(cluster.getSelectedBib())
				.switchIfEmpty( theBibs
					.mapNotNull( bibs -> bibs.size() > 0 ? bibs.get(0) : null )
					.map(BibRecord::getId)))
			
			.map(Objects::toString)
			.doOnSuccess(val -> log.debug("Primary bib: [{}]", val != null ? val : "No Data"));
		
		// Regenerate match points for all the bibs in the cluster.
		Flux<BibRecord> refingerprintBibs = theBibs
			.flatMapMany( Flux::fromIterable )
			.flatMap( bib -> {
				log.debug("Regenerate matchpoints for bib [{}]", bib.getId());
				return generateMatchPoints( bib )
					.collectList()
					.flatMap( matchPoints -> reconcileMatchPoints(matchPoints, bib) )
					.thenReturn(bib);
			});
		
		// The flow...
		return findBibToPreserve
			// No bibs attached to this cluster, ensure soft deleted
			.switchIfEmpty( manifestClusterRecord
				.flatMap( this::softDelete )
				.then(Mono.empty()))
			
			// Cluster has at least one bib, refingerprint all bibs.
			.flatMapMany( primaryBib -> refingerprintBibs
			  .filter( currentBib -> !currentBib.getId().toString().equals(primaryBib) ))
			
		  // Only none primary now.
			// Null out the contributes to, and save the bib (as an orphan).
			.map( nonePrimaryBib -> {

				log.debug("Null out cluster contribution for bib [{}]", nonePrimaryBib.getId());
				return nonePrimaryBib.setContributesTo(null);
			})
		  .flatMap( bibRecords::saveOrUpdate )
		  
		  // Find the source ID for each none-primary bib and flag it for (re)processing
		  .flatMap( bib -> {
		  	// Because of the use of LIKE in this query (because of the strangely loose identifiers),
		  	// we shouldn't assume 1 record. Here it's fairly safe to schedule all matches for "reprocessing"
		  	if (!log.isWarnEnabled()) {
		  	
		  		return bibRecords.findSourceRecordForBib(bib);
		  	}
		  	
		  	// Output a warning if we matched more than one source for a bib.
		  	return bibRecords.findSourceRecordForBib(bib)
		  		.collectList()
		  		.map( hits -> {
		  		  if (hits.size() != 1) {
		  		  	log.warn("Couldn't find single source record for bib [{}], found {} matches", bib.getId(), hits.size());
		  		  }
		  		  
		  		  return hits;
		  		})
		  		.flatMapMany(Flux::fromIterable);
		  })
		  
  		.map(SourceRecord::getId)
			.flatMap( sourceId -> Mono.justOrEmpty( sourceRecordService )
				.flatMap(sourceRecords -> {
					log.debug("Flag sourceRecord [{}] for reprocessing", sourceId);
					return sourceRecords.requireProcessing(sourceId);
				}))
			
			// If there were none-primary bibs then save the cluster.
			.then(Mono.fromSupplier(() -> {
				sharedIndexService.get().update( clusterID );
				return clusterID;
			}));
						
	}
}

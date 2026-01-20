package org.olf.dcb.core.clustering;

import static services.k_int.utils.TupleUtils.curry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.apache.commons.codec.language.DoubleMetaphone;
import org.olf.dcb.core.audit.ProcessAuditService;
import org.olf.dcb.core.clustering.matching.MatchpointService;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.clustering.model.MatchPoint;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.dataimport.job.SourceRecordService;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.indexing.SharedIndexService;
import org.olf.dcb.ingest.IngestService;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.olf.dcb.storage.MatchPointRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.debatty.java.stringsimilarity.JaroWinkler;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import services.k_int.events.ReactiveTransactionalBehaviours;
import services.k_int.features.FeatureFlag;

@Singleton
@FeatureFlag(ImprovedRecordClusteringService.FEATURE_IMPROVED_CLUSTERING)
@Replaces(DefaultRecordClusteringService.class)
public class ImprovedRecordClusteringService implements RecordClusteringService {

	private static enum MatchConfidence {
		HIGH,
		LOW
	}

	public static final String FEATURE_IMPROVED_CLUSTERING = "improved-clustering";

	private static final Logger log = LoggerFactory.getLogger(ImprovedRecordClusteringService.class);

	private static final double COMPARISON_THRESHOLD = 0.9;

	private final ClusterRecordRepository clusterRecords;
	private final Optional<SourceRecordService> sourceRecordService;
	private final BeanProvider<SharedIndexService> sharedIndexService;
	private final BibRecordService bibRecords;
	private final MatchpointService matchpointService;
	private final ReactiveTransactionalBehaviours transactionalBehaviours;
	private final ProcessAuditService processAuditService; 

	public ImprovedRecordClusteringService(
			ClusterRecordRepository clusterRecordRepository,
			BibRecordService bibRecordService,
			Environment environment,
			MatchPointRepository matchPointRepository,
			Optional<SourceRecordService> sourceRecordService,
			BeanProvider<SharedIndexService> sharedIndexService, MatchpointService matchpointservice, ReactiveTransactionalBehaviours transactionalBehaviours, ProcessAuditService genericAuditService) {
		this.clusterRecords = clusterRecordRepository;
		this.sourceRecordService = sourceRecordService;
		this.sharedIndexService = sharedIndexService;
		this.bibRecords = bibRecordService;
		this.matchpointService = matchpointservice;
		this.transactionalBehaviours = transactionalBehaviours;
		this.processAuditService = genericAuditService;

		log.info("***** Using Improved Clustering Service *****");
	}

	private static Comparator<Collection<ClusterRecord>> clusterCompare(Optional<ClusterRecord> currentCluster) {

		return (cluster1, cluster2) -> {

			int val1Size = cluster1.size();
			int val2Size = cluster2.size();

			int comp = Integer.compare(val2Size, val1Size);
			if (comp == 0 && (val1Size + val2Size) > 0) {
				var currentId = currentCluster.map(current -> current.getId().toString()).orElse(null);

				if (cluster1.iterator().next().getId().toString().equals(currentId)) {
					log.trace("Current cluster used as equal weight tie-breaker.");
					return -1;
				}

				if (cluster2.iterator().next().getId().toString().equals(currentId)) {
					log.trace("Current cluster used as equal weight tie-breaker.");
					return 1;
				}
			}

			return comp;
		};
	}

	// Get cluster record by id
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> findById(UUID id) {
		return Mono.from(clusterRecords.findById(id));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> softDelete(ClusterRecord theRecord) {
		log.debug("Soft delete {}", theRecord.getId());
		return Mono.justOrEmpty(theRecord)
				.filter(cr -> Objects.nonNull(cr.getId()))
				.flatMap(current ->
				// Create a new record for the deleted item.
				Mono.just(ClusterRecord.builder()
					.id(current.getId())
					.dateCreated(current.getDateCreated())
					.isDeleted(true)
					.build())

					.flatMap(deleted -> this.saveOrUpdate(deleted)
							.thenReturn(deleted))
					.doOnNext(cr -> log.debug("Soft deleted cluster {}", cr.getId())));
	}

	final Map<ReactiveTransactionStatus<?>, ConcurrentLinkedQueue<Runnable>> events = new ConcurrentHashMap<>();

	final Map<ReactiveTransactionStatus<?>, Mono<Void>> trxWatchers = new ConcurrentHashMap<>();

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Void> softDeleteByIdInList(Collection<UUID> ids) {
		return findAllByIdInList(ids)
				.flatMap(this::softDelete)
				.then();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Flux<ClusterRecord> findAllByIdInList(Collection<UUID> ids) {
		return Flux.from(clusterRecords.findAllByIdInList(ids));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Page<UUID>> findNextPageUpdatedBefore(@NonNull Instant before, Pageable page) {
		return Mono.from(clusterRecords.findIdByDateUpdatedLessThanEqualsOrderByDateUpdated(before, page));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Page<UUID>> findNextPageIndexedBefore(@NonNull Instant before, Pageable page) {
		return Mono.from(clusterRecords.findIdByLastIndexedIsNullOrLastIndexedLessThanOrderByDateUpdated(before, page));
	}
	
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Long> updateLastIndexed(@NonNull Collection<UUID> ids, @NonNull Instant before) {
		return Mono.from(clusterRecords.updateLastIndexed(ids, before));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Flux<ClusterRecord> findAllByIdInListWithBibs(Collection<UUID> ids) {
		return Flux.from(clusterRecords.findByIdInListWithBibs(ids));
	}

	// Remove the items in a new transaction.
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<ClusterRecord> mergeClusterRecords(ClusterRecord to, Collection<ClusterRecord> from) {

		return Mono.fromDirect(bibRecords.moveBetweenClusterRecords(from, to))
			.flatMap(cr -> softDeleteByIdInList(
				from
					.stream()
					.map(ClusterRecord::getId)
					.toList())
				.thenReturn(cr));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<ClusterRecord> reduceClusterRecords(final int pointsCreated,
			final List<ClusterRecord> matchedClusterList, Optional<ClusterRecord> currentCluster) {

		final var comp = clusterCompare(currentCluster);

		// Create a map with the id of the cluster against the number of times it
		// matched.
		return Flux.fromIterable(matchedClusterList)
			.collectMultimap(cr -> cr.getId().toString())
			.map(ocurrences -> {

				if ((log.isDebugEnabled() && ocurrences.size() > 1) || log.isTraceEnabled()) {
					ocurrences.forEach(
							(id, occs) -> log.debug("Matched Cluster [{}], on [{}] match points", id, occs.size()));
				}

				// Now take the entrySet and turn into an ordered list where the top result is
				// The highest matched on record
				final List<ClusterRecord> sortedOccurences = ocurrences.values().stream()
						.sorted(comp)
						.map(coll -> coll.iterator().next())
						.collect(Collectors.toList());

				// The list contains the sorted (weighted) matches via the match points. We should
				// add the current cluster as the lowest weighted cluster, if it wasn't matched by
				// the points.
				// This is likely because the previously seen bib was not matched, because the data
				// had changed so much. If we don't add this cluster, it would be left orphaned in
				// the database in certain circumstances.
				currentCluster
						.filter(current -> !ocurrences.containsKey(current.getId().toString()))
						.ifPresent(sortedOccurences::add);

				return sortedOccurences;
			})
			.flatMap(prioritisedClusters -> {

				if (prioritisedClusters.size() == 0) {
					log.trace("Didn't match any clusters in the database and no current cluster. Requires new.");
					return Mono.empty();
				}

				// First item is our cluster match
				var primary = prioritisedClusters.remove(0);
				if (prioritisedClusters.size() == 0) {

					if (log.isTraceEnabled() && currentCluster.isPresent()) {
						log.trace("Matched [{}], which is the current cluster");
					} else {
						log.trace("Matched [{}]", primary.getId());
					}

					return Mono.just(primary)
						.flatMap(processAuditService.withAuditMessage("Matched single cluster [%s]"
							.formatted(primary.getId())));
				}

				if (log.isDebugEnabled()) {
					log.debug("Matched [{}], and need to absorb [{}]", primary.getId(), prioritisedClusters.stream()
							.map(cr -> cr.getId().toString()).collect(Collectors.joining(" ,")));
				}
				
				return processAuditService.auditMessage("Matched cluster [%s] (merging with [%d] other clusters)".formatted(primary.getId(), prioritisedClusters.size()))
					.then(mergeClusterRecords(primary, prioritisedClusters));

			});
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<ClusterRecord> doSaveOrUpdate( final ClusterRecord cluster, boolean isUpdate ) {
		final Function<ClusterRecord, Publisher<? extends ClusterRecord>> saveMethod = isUpdate
				? clusterRecords::update
				: clusterRecords::save;

		final Mono<ClusterRecord> saveChain = Mono.just(cluster)
				.map(saveMethod)
				.flatMap(Mono::from);

		if (!sharedIndexService.isPresent()) {
			return saveChain;
		}

		return saveChain
			.transform(transactionalBehaviours.doOnCommittal(c -> {
				final UUID id = c.getId();

				if (Boolean.TRUE.equals(c.getIsDeleted())) {
					log.debug("Delete index for record [{}]", id);
					sharedIndexService.get().delete(c.getId());
					return;
				}

				if (isUpdate) {
					log.debug("Update index for record [{}]", id);
					sharedIndexService.get().update(id);
					return;
				}

				// Addition
				log.debug("Add index for record [{}]", id);
				sharedIndexService.get().add(c.getId());
			}));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> saveOrUpdate(final ClusterRecord cluster) {

		return Mono.just(cluster)
			.zipWhen(cr -> Mono.from(clusterRecords.existsById(cr.getId())))
			.flatMap(TupleUtils.function(this::doSaveOrUpdate));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Tuple2<BibRecord, ClusterRecord>> updateBibAndClusterData(BibRecord bib,
			Collection<MatchPoint> currentMatchPoints, ClusterRecord cluster) {

		// Save the cluster
		return Mono.just(cluster)
			.flatMap(this::saveOrUpdate)
			// Update the contribution and save the bib
			.zipWhen(cr -> Mono.just(bib.setContributesTo(cr))
				.flatMap(bibRecords::saveOrUpdate))
			// We can do some things at the same time.
			.flatMap(TupleUtils.function((cr, br) ->
				// Reconcile the matchpoints and elect the primary bib on the matched cluster
				Mono.zip(matchpointService.reconcileMatchPoints(currentMatchPoints, br), electSelectedBib(cr))
						.thenReturn(Tuples.of(br, cr))));
	}

	private ClusterRecord newClusterRecord(BibRecord bib) {
		return ClusterRecord.builder()
			.id(UUID.randomUUID())
			.title(bib.getTitle())
			.selectedBib(bib.getId())
			.build();
	}
	
	private final Queue<String> priorityReprocessingQueue = new ConcurrentLinkedQueue<>();
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Tuple2<List<ClusterRecord>, Optional<ClusterRecord>>> filterOutdatedClustersAndReprocess(List<ClusterRecord> potentialMatches, Optional<ClusterRecord> currentCluster) {
		
		var allClusters = potentialMatches.stream()
			.map(ClusterRecord::getId)
			.toList();
		
		return Flux.from( clusterRecords.getClusterIdsWithBibsPriorToVersionInList(IngestService.getProcessVersion(), allClusters))
			.transformDeferred(transactionalBehaviours.doOnCommittal(cluster -> {
				if (currentCluster.isPresent()) {
					if (cluster.equals(currentCluster.get().getId())) return;
				}
				priorityReprocessingQueue.offer(cluster.toString());
			}))
			.concatMap(processAuditService.withAuditMessage( c -> "Cluster [%s] contains outdated bibs, flag for reprocessing and ignore match at this time".formatted(c)), 0)
			.collectList()
			.flatMapMany( clustersToReprocess -> Flux.fromIterable( potentialMatches )
		  	.filter(cr -> !clustersToReprocess.contains( cr.getId() )))
			.collectList()
			.map(matches -> Tuples.of(matches, currentCluster));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Tuple2<BibRecord, ClusterRecord>> clusterUsingMatchPoints(BibRecord bib,
			Collection<MatchPoint> matchPoints) {
		return matchClusters(bib, matchPoints)
			.collectList()
			.zipWith(bibRecords.getClusterRecordForBib(bib.getId()).singleOptional())
			// Check to see if we want to reprocess any of these cluster records.
			// We need to if they contain any bibs not on the latest processing version.
			
			// Simply filter out those cluster records, allowing this bib to only match with
			// Up-to-date entries.
			.flatMap( TupleUtils.function( this::filterOutdatedClustersAndReprocess ))
			.flatMap( curry(matchPoints.size(), this::reduceClusterRecords) )
			.switchIfEmpty(Mono.just(bib)
				.flatMap(processAuditService.withAuditMessage("No existing cluster, create a new one"))
				.map(this::newOrExistingClusterRecord))
			.flatMap( curry(bib, matchPoints, this::updateBibAndClusterData) );
	}

	private ClusterRecord newOrExistingClusterRecord(BibRecord bib) {
		if (bib.getContributesTo() != null) {
			log.warn(
					"Asked to create a new cluster record because no other clusters matched, but the record already points at a cluster");
		}
		return newClusterRecord(bib);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Map<String, MatchPoint>> findAllCandidateMatchPoints(BibRecord bib, Collection<UUID> pointValues) {

		// Fetch all the candidate match points.
		return matchpointService.getAllCandidateMatchpointHits(bib, pointValues)
				.collectMap(mp -> mp.getValue().toString());
	}

	private static MatchConfidence initialMatchConfidence(MatchPoint mp) {

		final String domain = mp.getDomain().toUpperCase();

		if (MatchpointService.HIGH_CERTAINTY_IDS.test(domain))
			return MatchConfidence.HIGH;

		return MatchConfidence.LOW;
	}

	private static final JaroWinkler JW = new JaroWinkler();
	private static final DoubleMetaphone DM = new DoubleMetaphone();

	@FunctionalInterface
	private static interface StringSimilarityFunction extends BiFunction<String, String, Double> {}

	private static List<StringSimilarityFunction> similarityFunctions = List.of(
			JW::distance,
			ImprovedRecordClusteringService::metaphoneSimilarity);

	private static double metaphoneSimilarity(String s1, String s2) {
		// Similarity in metaphone helps us to match based on typos, without needing exact matches. 
		return DoubleStream.of(
			JW.distance(DM.doubleMetaphone(s1), DM.doubleMetaphone(s2)))
			.max()
			.orElse(0);
	}

	protected double compareBlockingTitles(BibRecord reference, BibRecord candidate) {
		
		// Compare the blocking titles.
		String refTitle = StringUtils.trimToNull( reference.getBlockingTitle() );
		String candidateTitle = StringUtils.trimToNull( candidate.getBlockingTitle() );
		
		// If either are null then return 0
		
		if (candidateTitle == null || refTitle == null) return 0;

		// Score is the average of applying all the similarity functions.
		final double score = similarityFunctions.stream()
			.mapToDouble(fun -> fun.apply(refTitle, candidateTitle))
			.average()
			.orElse(0d);
		
		if (!log.isDebugEnabled()) {
			log.debug("Comparing [{}] to [{}] yields similarity score of [{}]", refTitle, candidateTitle, score);
		}
		
		return score;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Flux<ClusterRecord> doCompareBibs(BibRecord compareTo, Map<BibRecord, List<MatchPoint>> clusterBibs,
			List<ClusterRecord> alreadyMatchedClusers) {

		return Mono.just(clusterBibs)
			.flatMapMany(bibs -> {
//				final Set<ClusterRecord> matches = new HashSet<>();
				final Set<ClusterRecord> matches = new HashSet<>();
				final List<String> auditLog = new ArrayList<>();

				bibs.forEach((candidate, points) -> {
					
					if (candidate.getContributesTo() == null)
						return;
					
					if (matches.contains(candidate.getContributesTo()))
						return;


					boolean hasTitleMatch = false;
					boolean hasNoneTitleMatch = false;

					for (var mp : points) {
						// Match points are exact matches only. We'll deal with these first. Exact
						// title-type matches still
						// require other matches to be considered.
						if (mp.getDomain().toUpperCase().endsWith("TITLE")) {
							// Matched a title of some kind.
							hasTitleMatch = true;

						} else {
							hasNoneTitleMatch = true;
						}

						// If we have a title and none title match we can leave early.
						if (hasTitleMatch && hasNoneTitleMatch)
							break; // Exit if we've learned enough.
					}

					// If we have a none title match but no title match... We can compare by reading
					// in the candidate data
					if (hasNoneTitleMatch) {
						auditLog.add("One or more low confidence matches with bib [%s], check title-type properties...".formatted(candidate.getId()));
						
						if (hasTitleMatch) {
							// Add the cluster record as a match and return.
							auditLog.add("Exact match on at least one title-type property with bib [%s]. Match!".formatted(candidate.getId()));
							
							matches.add(candidate.getContributesTo());

						} else {
							// Need do to a data comparison of the title-types.
							log.info(
								"Potential match between [{}] and [{}] required deeper examination",
								compareTo.getId(), candidate.getId());
							
							double matchScore = compareBlockingTitles(compareTo, candidate);
							if (matchScore >= COMPARISON_THRESHOLD) {
								var cluster = candidate.getContributesTo();
								if (log.isDebugEnabled()) {
									log.debug(
											"Potential bib match of [{}] with [{}] scored [{}]. Match cluster [{}]",
											compareTo.getId(), candidate.getId(), matchScore,
											cluster.getId());
								}
								auditLog.add("Blocking title similarity [%#.2f%%] with bib [%s] greater or equal to threashold [%#.2f%%]. Match!".formatted(
										(matchScore * 100d),	candidate.getId(), (COMPARISON_THRESHOLD * 100d) ));
								
								// Add to secondary matches
								matches.add(cluster);
							} else {
								if (log.isDebugEnabled()) {
									log.debug(
										"Potential bib match of [{}] with [{}] scored [{}] which is lower than the [{}] threshold. None-match",
										compareTo.getId(), candidate.getId(), matchScore, COMPARISON_THRESHOLD);
								}
								auditLog.add("Blocking title similarity [%#.2f%%] with bib [%s] less than threashold [%#.2f%%]. No Match".formatted(
										matchScore,	candidate.getId(), COMPARISON_THRESHOLD));
							}
						}
					}
				});

				// Flux of the matches...
				return Flux.fromIterable( auditLog )
					.concatMap( processAuditService::auditMessage, 0)
					.thenMany(Flux.fromIterable(matches));
			});
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Flux<ClusterRecord> doExtraProcessing(BibRecord compareTo, Collection<MatchPoint> candidates,
			List<ClusterRecord> alreadyMatchedClusers) {

		if (candidates.isEmpty()) {
			log.debug("No extra processing for bib [{}] required", compareTo.getId());

			return Flux.empty();
		}
		
		// Mutate the map making the manifested bib the key and not just the ID
		return Mono.just(candidates.stream()
			.filter( mp -> !mp.getBibId().equals(compareTo.getId()) )
			.collect(Collectors.groupingBy(mp -> mp.getBibId().toString())))

			// Manifest the bibs in the map, and that aren't attached to clusters that
			// we've already decided to match with.
			.flatMap(bibIdMap -> Mono.just(bibIdMap.keySet().stream().map(UUID::fromString).toList())
				.flatMapMany( ids -> chunkLoadBibsAndCluster(ids, 200) )
				.filter(bib -> alreadyMatchedClusers.contains(bib.getContributesTo()))
				.collectMap(bib -> bib.getId().toString())
				.map(manifestedBibs -> {
					Map<BibRecord, List<MatchPoint>> bibMap = new LinkedHashMap<>();
					manifestedBibs.forEach((bid, bib) -> bibMap
							.put(bib, bibIdMap.get(bid)));
					return bibMap;
				}))
			.flatMapMany(bibsToCheck -> doCompareBibs(compareTo, bibsToCheck, alreadyMatchedClusers));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Flux<ClusterRecord> matchClusters(BibRecord bib, Collection<MatchPoint> matchPoints) {
		log.trace("match clusters : {}", matchPoints);

		if (matchPoints.size() == 0)
			log.error("0 match points from {}", bib);

		return Flux.fromIterable(matchPoints)
			.collectMultimap( ImprovedRecordClusteringService::initialMatchConfidence )
			.flatMapMany( rankedPoints -> getListOfMatchedClusters(bib, rankedPoints) );
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Flux<ClusterRecord> getListOfMatchedClusters(BibRecord bib, Map<MatchConfidence, Collection<MatchPoint>> rankedPoints) {
		
		final Map<UUID, MatchPoint> matchPointValueMap = new HashMap<>();
		
		// Create a map of matchpoints with the value as the key so we can easily look up the matches and produce an audit log message
		rankedPoints.forEach((confidence, mps) -> {
			mps.forEach( mp -> {
				matchPointValueMap.put(mp.getValue(), mp);
			});
		});
		
		// Set of unique high-confidence values.
		final var highConfidenceValues = rankedPoints.getOrDefault(MatchConfidence.HIGH, Collections.emptySet())
			.stream()
			.map(MatchPoint::getValue)
			.collect(Collectors.toUnmodifiableSet());
		
		return matchpointService.getMatchesByDerrivedType(bib.getDerivedType(), highConfidenceValues)
			.filter( mp -> !mp.getBibId().equals(bib.getId()) )
			.concatMap( processAuditService.withAuditMessage( match -> {
				// The match here is the matchpoint from the database so contains no extra debug information
				// we can look it up from the map to add an audit message without polluting the database with
				// debug only data.
				var matchSource = matchPointValueMap.get(match.getValue());
				
//				matchPointValueMap.get(match.)
				
				return "High confidence match with Bib [%s] on [%s] = [%s]".formatted(
					match.getBibId(),
					matchSource.getDomain(), matchSource.getSourceValueHint());
			}), 0)
			.map(MatchPoint::getBibId)
			.collect(Collectors.toUnmodifiableSet())
			.flatMapMany( ids -> chunkLoadBibsAndCluster(ids, 200) )
			.mapNotNull( BibRecord::getContributesTo )
			
			// Returns a list of ClusterRecord matches. The re-occurences of Clusters is
			// important as it equates to the number of matches. i.e. If it's in the list
			// twice it was matched on 2 match points.
			// Hence List and NOT Set
			.collectList()
			.flatMapMany(primaryMatchedClusters -> Flux.fromIterable(primaryMatchedClusters)
				.concatWith(
					doExtraProcessing(bib, rankedPoints.computeIfAbsent(MatchConfidence.LOW,
						_k -> Collections.emptySet()), primaryMatchedClusters)));
	}
	
	@Transactional(propagation = Propagation.REQUIRED)
	protected Flux<BibRecord> chunkLoadBibsAndCluster( Collection<UUID> ids, int max ) {
		return Flux.fromIterable(ids)
			.buffer(500)
			.concatMap( bibRecords::findAllIncludingClusterByIdIn );
	}

	@Override
	@Transactional(propagation = Propagation.NESTED)
	public Mono<BibRecord> clusterBib(final BibRecord bib) {

		return matchpointService.generateMatchPoints(bib)
			.collectList()
			.flatMap(curry(bib, this::clusterUsingMatchPoints))
			.map(TupleUtils.function((savedBib, savedCluster) -> {
				log.trace("Cluster {} selected for bib {}", savedCluster, savedBib);
				return savedBib;
			}))
			// Set the subject for the audit messages produced upstream
			// The process ID should already be set by the job initiator.
			.transform( processAuditService.withProcessAudit(process -> process
				.processSubject(bib.getId())) );
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
	protected Mono<ClusterRecord> electSelectedBib(final ClusterRecord cr) {
		return electSelectedBib(cr, Optional.empty());
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> electSelectedBib(final ClusterRecord cr, final Optional<BibRecord> ignoreBib) {
		// Use the record with the highest score
		return bibRecords.findTop2HighestScoringContributorId(cr)
			.filter(id -> ignoreBib
				.map(ignore -> !id.toString().equals(ignore.getId().toString()))
				.orElse(Boolean.TRUE))

			.next()
			.map(contrib -> {
				log.trace("Setting selected bib on cluster record {} to {}", cr.getId(), contrib);
				return cr.setSelectedBib(contrib);
			})
			.defaultIfEmpty(cr)
			.flatMap(this::saveOrUpdate);
	}
	
	@NonNull
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Mono<Void> reprocessBibsWithNoCluster() {
		return Mono.from( clusterRecords.reprocessOrphanedBibs() )
			.doOnSuccess( num -> log.info("Flagged [{}] bibs with no cluster for reprocessing", num) )
			.then();
	}

	/**
	 * Flag that this cluster needs reprocessing.
	 * 
	 * Actual flow is to orphan all the bibs 
	 */
	@Override
	@Retryable
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Mono<UUID> disperseAndRecluster(@NotNull @NonNull UUID clusterID) {

		// Cluster record
		Mono<ClusterRecord> manifestClusterRecord = findById(clusterID).cache();

		// Cluster bibs
		Flux<BibRecord> theBibs = Mono.defer(() -> manifestClusterRecord)
			.flatMapMany(bibRecords::findAllByContributesTo)
			.concatMap( bibRecords::checkOrphanedBib )
			.cache();

		// Get the selected bib, or the first from all bibs if no selected item.
		// This will let us keep the existing cluster.
		Mono<String> findBibToPreserve = manifestClusterRecord
			.mapNotNull(ClusterRecord::getSelectedBib)
			.switchIfEmpty(theBibs
				.next()
				.map(BibRecord::getId))

			.map(Objects::toString)
			.doOnSuccess(val -> {
				if (val == null) log.warn("No primary bib for cluster [{}]", clusterID);
			});

		// Regenerate match points for all the bibs in the cluster.
//		Flux<BibRecord> refingerprintBibs = theBibs
//			.flatMap(bib -> {
//				log.debug("Regenerate matchpoints for bib [{}]", bib.getId());
//				return matchpointGenerator.generateMatchPoints(bib)
//					.collectList()
//					.flatMap(matchPoints -> matchpointGenerator.reconcileMatchPoints(matchPoints, bib))
//					.thenReturn(bib);
//			});
		
		
		// First thing to do is to delete all orphaned bibs from the cluster

		return findBibToPreserve
			// No bibs attached to this cluster, ensure soft deleted
			.switchIfEmpty(manifestClusterRecord
				.flatMap(this::softDelete)
				.then(Mono.empty()))
			
			// Cluster has at least one bib, refingerprint all bibs.
			.flatMapMany(primaryBib -> theBibs
				.map(currentBib -> {
					
					// Leave primary bib owner unchanged.
					if (currentBib.getId().toString().equals(primaryBib)) {
						log.debug("Leave cluster contribution for bib [{}], as it's the primary contibutor", currentBib.getId());
						return currentBib; 
					}
					
					log.debug("Null out cluster contribution for bib [{}]", currentBib.getId());
					return currentBib.setContributesTo(null);
				})
				.distinct(br -> br.getId().toString())
				.flatMap(currentBib -> {
					log.debug("Saving bib [{}]", currentBib.getId());
					return bibRecords.saveOrUpdate(currentBib);
				})
				.sort((br1, br2) -> br1.getContributesTo() != null ? 1 : 0)
				.concatMap(bib -> {
					// Because of the use of LIKE in this query (because of the strangely loose
					// identifiers),
					// we shouldn't assume 1 record. Here it's fairly safe to schedule all matches
					// for "reprocessing"
					if (!log.isWarnEnabled()) {

						return bibRecords.findSourceRecordForBib(bib);
					}

					// Output a warning if we matched more than one source for a bib.
					return bibRecords.findSourceRecordForBib(bib)
							.collectList()
							.map(hits -> {
								if (hits.size() != 1) {
									log.warn("Couldn't find single source record for bib [{}], found {} matches",
											bib.getId(), hits.size());
								}

								return hits;
							})
							.flatMapMany(Flux::fromIterable);
				})

				.map(SourceRecord::getId)
				.flatMap(sourceId -> Mono.justOrEmpty(sourceRecordService)
					.flatMap(sourceRecords -> {
						log.debug("Flag sourceRecord [{}] for reprocessing", sourceId);
						return sourceRecords.requireProcessing(sourceId);
					})))
			.then(Mono.fromSupplier(() -> {
				sharedIndexService.get().update(clusterID);
					return clusterID;
				}));
	}

	@Override
	public Flux<MatchPoint> generateMatchPoints(BibRecord bibRecord) {
		return matchpointService.generateMatchPoints(bibRecord);
	}
}

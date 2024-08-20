package org.olf.dcb.core.svc;

import static org.olf.dcb.utils.DCBStringUtilities.generateBlockingString;
import static org.olf.dcb.utils.DCBStringUtilities.uuid5ForIdentifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.model.BibIdentifier;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.ingest.model.Identifier;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.processing.ProcessingStep;
import org.olf.dcb.stats.StatsService;
import org.olf.dcb.storage.BibIdentifierRepository;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.MatchPointRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.annotation.Timed;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import services.k_int.utils.Functions;
import services.k_int.utils.ReactorUtils;


@Singleton
public class BibRecordService {
	
	public static final int PROCESS_VERSION = 1;

	private static final Logger log = LoggerFactory.getLogger(BibRecordService.class);

	private final BibRepository bibRepo;

	private final BibIdentifierRepository bibIdentifierRepo;
	private final BeanProvider<RecordClusteringService> recordClusteringServiceProvider;
	
	private final StatsService statsService;

	private final MatchPointRepository matchPointRepository;


	public BibRecordService(
	  BibRepository bibRepo,
	  BibIdentifierRepository bibIdentifierRepository,
		StatsService statsService, BeanProvider<RecordClusteringService> recordClusteringServiceProvider, MatchPointRepository matchPointRepository) {
		this.bibRepo = bibRepo;
		this.bibIdentifierRepo = bibIdentifierRepository;
		this.recordClusteringServiceProvider = recordClusteringServiceProvider;
		this.statsService = statsService;
		this.matchPointRepository = matchPointRepository;
	}

	private BibRecord step1(final BibRecord bib, final IngestRecord imported) {
		// log.info("Executing step 1");

		bib.setProcessVersion(PROCESS_VERSION);

		return bib;
	}

	private BibRecord minimalRecord(final IngestRecord imported) {

		return BibRecord.builder().id(imported.getUuid()).title(imported.getTitle())
				.sourceSystemId(imported.getSourceSystem().getId())
				.sourceRecordId(imported.getSourceRecordId())
				.recordStatus(imported.getRecordStatus())
				.typeOfRecord(imported.getTypeOfRecord())
				.derivedType(imported.getDerivedType())
				.blockingTitle(generateBlockingString(imported.getTitle()))
				.canonicalMetadata(imported.getCanonicalMetadata())
        .metadataScore(imported.getMetadataScore())
        .build();
	}

	@Transactional
	public Mono<BibRecord> update(final BibRecord record) {
		return Mono.from(bibRepo.update(record));
	}

	@Transactional
	public Mono<BibRecord> saveOrUpdate(final BibRecord record) {
		return Mono.from(bibRepo.existsById(record.getId()))
			.map(exists -> exists ? bibRepo.update(record) : bibRepo.save(record))
			.flatMap(Mono::from);
	}
	
	@Transactional
	public Mono<BibRecord> getById(final UUID id) {
		return Mono.just(id)
			.map(bibRepo::getById)
			.flatMap(Mono::from);
	}

	// https://github.com/micronaut-projects/micronaut-data/discussions/1405
	@Transactional
	public Mono<BibRecord> getOrSeed(final IngestRecord source) {
		return Mono.fromDirect(bibRepo.findById(source.getUuid()))
			.defaultIfEmpty(minimalRecord(source));
	}

	public Flux<BibRecord> findAllByContributesTo(final ClusterRecord clusterRecord) {
		return Flux.from(bibRepo.findAllByContributesTo(clusterRecord));
	}
	
	public Flux<BibRecord> findAllByContributesToInList(final Collection<ClusterRecord> contributesTo) {
		return Flux.from(bibRepo.findAllByContributesToInList(contributesTo));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> getClusterRecordForBib(UUID bibId) {
		return Mono.from(bibRepo.findContributesToById(bibId));
	}

	public Flux<BibIdentifier> findAllIdentifiersForBib(BibRecord owner) {
		return Flux.from(bibIdentifierRepo.findAllByOwner(owner));
	}

	@Transactional
	protected Mono<BibRecord> saveIdentifiers(BibRecord savedBib, IngestRecord source) {
		return Flux.fromIterable(source.getIdentifiers())
				.map(id -> ingestRecordIdentifierToModel(id, savedBib))
				.flatMap(this::saveOrUpdateIdentifier)
				.then(Mono.just(savedBib));
	}

	protected Mono<BibRecord> updateStatistics(BibRecord savedBib, IngestRecord source, long start_time) {

//		long elapsed = System.currentTimeMillis() - start_time;

		// log.debug("update statistics {} {} {} {}",elapsed, savedBib.getId(), savedBib.getDateCreated(), savedBib.getDateUpdated());
		if ( savedBib.getDateCreated().equals(savedBib.getDateUpdated())) {
			statsService.notifyEvent("BibInsert",source.getSourceSystem().getCode());
			// insert
		}
		else {
			statsService.notifyEvent("BibUpdate",source.getSourceSystem().getCode());
			// update
		}
		return Mono.just(savedBib);
	}

	@Transactional
	protected Mono<BibIdentifier> saveOrUpdateIdentifier(BibIdentifier bibIdentifier) {
		// log.debug("saveOrupdateIdentifier {}
		// {}",bibIdentifier,bibIdentifier.getId());
		return Mono.from(bibIdentifierRepo.existsById(bibIdentifier.getId())).flatMap(exists -> Mono
				.fromDirect(exists ? bibIdentifierRepo.update(bibIdentifier) : bibIdentifierRepo.save(bibIdentifier)));
	}

	private BibIdentifier ingestRecordIdentifierToModel(Identifier id, BibRecord owner) {
		return BibIdentifier.builder().id(uuid5ForIdentifier(id.getNamespace(), id.getValue(), owner.getId())).owner(owner)
				.value(id.getValue() != null ? id.getValue().substring(0, Math.min(id.getValue().length(), 254)) : null)
				.namespace(
						id.getNamespace() != null ? id.getNamespace().substring(0, Math.min(id.getNamespace().length(), 254))	: null)
				.build();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<ClusterRecord> moveBetweenClusterRecords(Collection<ClusterRecord> fromAll, ClusterRecord to) {
		return Mono.fromDirect(bibRepo.updateByContributesToInList(fromAll, to)).thenReturn(to);
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Long> deleteBibIdentifers( BibRecord bib ) {
		
		return Mono.from( bibIdentifierRepo.deleteAllByOwner(bib) );
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Long> deleteBibMatchPoints( BibRecord bib ) {
		
		return Mono.from( matchPointRepository.deleteAllByBibId(bib.getId()) );
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<BibRecord> deleteRelatedItems( @NonNull final BibRecord bib ) {
		
		return Mono.zip( deleteBibIdentifers( bib ), deleteBibMatchPoints( bib ) )
			.map(TupleUtils.function( ( idCount, matchPointCount ) -> {
				log.debug("Removed {} identifiers and {} match-points for bib", idCount, matchPointCount );
				return idCount + matchPointCount;
			}))
			.thenReturn(bib);
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<BibRecord> deleteBibAndUpdateCluster(@NonNull BibRecord bib) {
		return Mono.justOrEmpty( bib.getId() )
				.flatMap( this::getClusterRecordForBib )
				.flatMap( cr -> findAllByContributesTo(cr)
					.count()
					.flatMap( size -> size > 1 ? recordClusteringServiceProvider.get()
							.electSelectedBib(cr, Optional.ofNullable(bib))
							.then(Mono.empty()) : Mono.just( cr ) )
				)
				.doOnNext( cr -> log.debug("Soft deleteing cluster record {} as single referenced bib to be deleted.", cr.getId()) )
				.flatMap( recordClusteringServiceProvider.get()::softDelete )
				.then( Mono.defer(() -> {
					log.debug("Deleteing bib [{}]", bib.getId());
					return deleteBibAndRelations(bib).thenReturn(bib); 
				}))
			;
	}
	
	@Transactional
	protected Mono<Void> deleteBibAndRelations(@NonNull BibRecord bib) {
		return deleteRelatedItems( bib )
			.map(BibRecord::getId)
			.map(bibRepo::delete)
			.flatMap(Mono::from);
	}
	
	@Timed("bib.process")
	@SingleResult
	@Transactional(propagation = Propagation.MANDATORY)
	public Publisher<BibRecord> process(final IngestRecord source) {

    log.debug("BibRecordService::process(source={}, sourceRecordId={}, clusterid={}, title={}, suppress:{}, deleted:{})",
        source.getSourceSystem().getCode(),
        source.getSourceRecordId(),
        source.getClusterRecordId(),
        source.getTitle(),
        source.getSuppressFromDiscovery(),
        source.getDeleted()
        );


		statsService.notifyEvent("IngestRecord",source.getSourceSystem().getCode());

		// we could possibly use .elapsed() for this in the future
		long start_time = System.currentTimeMillis();

		// SO: TODO: Make this a predicate further upstream, to filter out the source resource.
		// Will allow us to bail out of the resource early.
		// We can raise events from with in that for the side effects.
		// Add in some processing to abort if we don't have a title - probably we should treat this as a delete signal
		
		if ( StringUtils.trimToNull(source.getTitle()) == null) {
			statsService.notifyEvent("DroppedNullTitle",source.getSourceSystem().getCode());
			log.warn("Record {} with empty title - bailing", source);
			return Mono.empty();
		}
		
		return Mono.just(source)
			.zipWhen( this::getOrSeed )
			.flatMap(TupleUtils.function(( ir, bib ) -> {

				final boolean suppressed = Boolean.TRUE.equals( ir.getSuppressFromDiscovery() );
				final boolean deleted = Boolean.TRUE.equals( ir.getDeleted() );
				
				if ( suppressed || deleted ) {
					// Suppress...
					statsService.notifyEvent("DroppedTitle",source.getSourceSystem().getCode());
					log.debug("Record {} flagged as {}, ensure we redact accordingly.", source, deleted ? "deleted" : "suppressed");
					
					return deleteBibAndUpdateCluster(bib).then( Mono.empty() );
				}
				
				// Default to just re-emitting the bib.
				return Mono.just(bib);
			}))
			.flatMap((final BibRecord bib) -> {
				final List<ProcessingStep> pipeline = new ArrayList<>();
				pipeline.add(this::step1);
				return Flux.fromIterable(pipeline).reduce(bib, (theBib, step) -> step.apply(bib, source));
			})
		.flatMap(this::saveOrUpdate)
		.flatMap(savedBib -> this.saveIdentifiers(savedBib, source))
		.flatMap( finalBib -> this.updateStatistics(finalBib, source, start_time) );
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Flux<UUID> findTop2HighestScoringContributorId( @NonNull ClusterRecord cr  ) {
		return Flux.from( bibRepo.findTop2ByContributesToOrderByMetadataScoreDesc(cr) )
				.map( BibRecord::getId );
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Page<BibRecord>> getPageOfHostLmsBibs ( @NonNull UUID sourceSystemId, @NonNull Pageable page ) {
		return Mono.from( bibRepo.findAllBySourceSystemId(sourceSystemId, page) );
	}

	public Publisher<Void> cleanup() {
		return bibRepo.cleanUp();
	}

	public Publisher<Void> commit() {
		return bibRepo.commit();
	}

}

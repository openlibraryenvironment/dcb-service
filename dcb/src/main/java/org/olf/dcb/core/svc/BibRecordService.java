package org.olf.dcb.core.svc;

import static org.olf.dcb.utils.DCBStringUtilities.generateBlockingString;
import static org.olf.dcb.utils.DCBStringUtilities.uuid5ForIdentifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.model.BibIdentifier;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.svc.RecordClusteringService.MissingAvailabilityInfo;
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

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;


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
    // log.info("Saving identifiers for {}",savedBib != null ? savedBib.getId() : "null" );
		return Flux.fromIterable(source.getIdentifiers())
				.map(id -> ingestRecordIdentifierToModel(id, savedBib))
        .distinct()
        // .doOnNext(id -> log.info("processing identifier {} {} {}",id.getValue(),id.getNamespace(),id.getConfidence()))
				.flatMap(this::saveOrUpdateIdentifier)
        .map(BibIdentifier::getId)
        .collectList()
        .flatMap( savedIdList -> purgeDeletedIdentifiers(savedBib,savedIdList));
	}

	@Transactional
	protected Mono<BibRecord> purgeDeletedIdentifiers(BibRecord savedBib, List<UUID> validIdentifierIdList) {
    return Mono.from(bibIdentifierRepo.deleteAllByOwnerIdAndIdNotIn(savedBib.getId(), validIdentifierIdList))
      .doOnNext( del -> {
        if (del > 0) 
          log.info("Purged {} existing identifiers that are no longer valid from {}", del, savedBib.getId());
        // else
        //   log.info("No removals for {}", savedBib.getId());
      })
			.then(Mono.just(savedBib));
  }

	protected Mono<BibRecord> updateStatistics(BibRecord savedBib, IngestRecord source) {

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
	  // log.debug("saveOrupdateIdentifier {} {} {} hc={}",bibIdentifier.getNamespace()+":"+bibIdentifier.getValue(),bibIdentifier.getId(),bibIdentifier.hashCode());
		return Mono.from(bibIdentifierRepo.existsById(bibIdentifier.getId()))
      .flatMap(exists -> Mono
				.fromDirect(exists ? 
          updateIdentifier(bibIdentifier) : 
          saveIdentifier(bibIdentifier)))
        .doOnError( e -> log.error("Problem saving identifier "+bibIdentifier.getNamespace()+":"+bibIdentifier.getValue()+" "+bibIdentifier.getConfidence()+" "+bibIdentifier.hashCode()));
	}

  private Publisher<? extends BibIdentifier> saveIdentifier(BibIdentifier bibIdentifier) {
    return bibIdentifierRepo.save(bibIdentifier);
  }

  private Publisher<? extends BibIdentifier> updateIdentifier(BibIdentifier bibIdentifier) {
    return bibIdentifierRepo.update(bibIdentifier);
  }

	private BibIdentifier ingestRecordIdentifierToModel(Identifier id, BibRecord owner) {

		return BibIdentifier
      .builder()
      .id(uuid5ForIdentifier(id.getNamespace(), id.getValue(), owner.getId()))
      .owner(owner)
			.value(id.getValue() != null ? id.getValue().substring(0, Math.min(id.getValue().length(), 254)) : null)
			.namespace(id.getNamespace() != null ? id.getNamespace().substring(0, Math.min(id.getNamespace().length(), 254))	: null)
			.confidence(id.getConfidence())
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
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<BibRecord> deleteAssociatedBib (final IngestRecord source) {
		return getOrSeed(source).
				flatMap(this::deleteBibAndUpdateCluster);
	}
	
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<BibRecord> process(final IngestRecord source) {
		
		if (log.isTraceEnabled()) {
			log.trace("BibRecordService::process(source={}, sourceRecordId={}, clusterid={}, title={}, suppress:{}, deleted:{})",
	        source.getSourceSystem().getCode(),
	        source.getSourceRecordId(),
	        source.getClusterRecordId(),
	        source.getTitle(),
	        source.getSuppressFromDiscovery(),
	        source.getDeleted()
	        );
		}
		
		final boolean suppressed = Boolean.TRUE.equals( source.getSuppressFromDiscovery() );
		final boolean deleted = Boolean.TRUE.equals( source.getDeleted() );
		
		if ( suppressed || deleted ) {

      log.info("Processing a delete");
			
			// Suppress...			
			return deleteAssociatedBib(source)
				.flatMap( deletedBib -> {
					statsService.notifyEvent("DroppedTitle",source.getSourceSystem().getCode());
					
					if (log.isDebugEnabled()) {
						final String removalReason = deleted ? "deleted" : "suppressed";
						log.debug("removal -- Record {} flagged as {}, redact accordingly", source, removalReason);
					}
					
					return Mono.empty();
				});
		}
		
		if ( StringUtils.trimToNull(source.getTitle()) == null ) {
      log.info("Processing an empty title");
			return Mono.just(source)
				.flatMap( s -> {
					statsService.notifyEvent("DroppedNullTitle",s.getSourceSystem().getCode());
					if (log.isTraceEnabled()) {
						log.warn("None deleted record {} with empty title - bailing", s);
					} else {
						log.warn("None deleted record {} with empty title - bailing", s.getUuid());
					}
					return Mono.empty();
				});
		}
		
		// None deleted or suppressed items...
		
    //  log.info("Progress to get or seed");
		return getOrSeed(source)
			.map( bib -> {
				statsService.notifyEvent("IngestRecord",source.getSourceSystem().getCode());
				return bib;
			})
			.flatMap(bib -> {
				
				final List<ProcessingStep> pipeline = new ArrayList<>();
				pipeline.add(this::step1);
				return Flux.fromIterable(pipeline).reduce(bib, (theBib, step) -> step.apply(bib, source));
			})
		 .flatMap(this::saveOrUpdate)
		 .flatMap(savedBib -> this.saveIdentifiers(savedBib, source))
		 .flatMap(finalBib -> this.updateStatistics(finalBib, source) );
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
	
	@Transactional
	public Flux<BibRecord> getAllByIdIn(@NonNull Collection<UUID> ids) {
		return Flux.from( bibRepo.getAllByIdIn(ids) );
	}
	
	@Transactional
	public Mono<Page<BibRecord>> getPageOfBibs(@NonNull Pageable page) {
		return Mono.from( bibRepo.queryAll(page) );
	}

	public Flux<MissingAvailabilityInfo> findMissingAvailability ( int limit ) {
		return Flux.from( bibRepo.findMissingAvailability( limit ));
	}

	public Publisher<Void> cleanup() {
		return bibRepo.cleanUp();
	}

	public Publisher<Void> commit() {
		return bibRepo.commit();
	}

}

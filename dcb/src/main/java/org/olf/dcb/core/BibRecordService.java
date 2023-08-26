package org.olf.dcb.core;

import static org.olf.dcb.utils.DCBStringUtilities.generateBlockingString;
import static org.olf.dcb.utils.DCBStringUtilities.uuid5ForIdentifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import org.olf.dcb.core.model.BibIdentifier;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.ingest.model.Identifier;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.processing.ProcessingStep;
import org.olf.dcb.stats.StatsService;
import org.olf.dcb.storage.BibIdentifierRepository;
import org.olf.dcb.storage.BibRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;

@Singleton
public class BibRecordService {
	
	public static final int PROCESS_VERSION = 1;

	private static final Logger log = LoggerFactory.getLogger(BibRecordService.class);

	private final BibRepository bibRepo;

	private final BibIdentifierRepository bibIdentifierRepo;

	private final StatsService statsService;

	private final ConversionService conversionService;

	BibRecordService(BibRepository bibRepo,
	        BibIdentifierRepository bibIdentifierRepository,
		StatsService statsService,
                ConversionService conversionService) {
		this.bibRepo = bibRepo;
		this.bibIdentifierRepo = bibIdentifierRepository;
		this.statsService = statsService;
		this.conversionService = conversionService;
	}

	private BibRecord step1(final BibRecord bib, final IngestRecord imported) {
		// log.info("Executing step 1");

		bib.setProcessVersion(PROCESS_VERSION);

		return bib;
	}

	private BibRecord minimalRecord(final IngestRecord imported) {

		return BibRecord.builder().id(imported.getUuid()).title(imported.getTitle(conversionService))
				.sourceSystemId(imported.getSourceSystem().getId())
				.sourceRecordId(imported.getSourceRecordId())
				.recordStatus(imported.getRecordStatus(conversionService))
				.typeOfRecord(imported.getTypeOfRecord())
				.derivedType(imported.getDerivedType(conversionService))
				.blockingTitle(generateBlockingString(imported.getTitle(conversionService)))
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
				.flatMap(exists -> Mono.fromDirect(exists ? bibRepo.update(record) : bibRepo.save(record)));
	}

	// https://github.com/micronaut-projects/micronaut-data/discussions/1405
	@Transactional
	public Mono<BibRecord> getOrSeed(final IngestRecord source) {
		return Mono.fromDirect(bibRepo.findById(source.getUuid())).defaultIfEmpty(minimalRecord(source));
	}

	public Flux<BibRecord> findAllByContributesTo(final ClusterRecord clusterRecord) {
		return Flux.from(bibRepo.findAllByContributesTo(clusterRecord));
	}

	public Mono<ClusterRecord> getClusterRecordForBib(UUID bibId) {
		return Mono.from(bibRepo.findContributesToById(bibId));
	}

	public Flux<BibIdentifier> findAllIdentifiersForBib(BibRecord owner) {
		return Flux.from(bibIdentifierRepo.findAllByOwner(owner));
	}

	@Transactional
	protected Mono<BibRecord> saveIdentifiers(BibRecord savedBib, IngestRecord source) {
		return Flux.fromIterable(source.getIdentifiers()).map(id -> ingestRecordIdentifierToModel(id, savedBib))
				.flatMap(this::saveOrUpdateIdentifier).then(Mono.just(savedBib));
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

	public Mono<ClusterRecord> moveBetweenClusterRecords(Collection<ClusterRecord> fromAll, ClusterRecord to) {
		return Mono.fromDirect(bibRepo.updateByContributesToInList(fromAll, to)).thenReturn(to);
	}

	@Transactional(value = TxType.REQUIRES_NEW)
	public Publisher<BibRecord> process(final IngestRecord source) {

		// log.debug("BibRecordService::process(...clusterid={})",source.getClusterRecordId());

		statsService.notifyEvent("IngestRecord",source.getSourceSystem().getCode());

		// we could possibly use .elapsed() for this in the future
		long start_time = System.currentTimeMillis();

		// SO: TODO: Make this a predicate further upstream, to filter out the source resource.
		// Will allow us to bail out of the resource early.
		// We can raise events from with in that for the side effects.
		// Add in some processing to abort if we don't have a title - probably we should treat this as a delete signal
		if ( ( source.getTitle(conversionService) == null ) || 
         ( source.getTitle(conversionService).length() == 0 ) ||
         ( ( source.getSuppressFromDiscovery() != null ) && ( source.getSuppressFromDiscovery().equals(Boolean.TRUE) ) ) ||
         ( ( source.getDeleted() != null ) && ( source.getDeleted().equals(Boolean.TRUE) ) ) ) {
			// Future development: We should probably signal this records source:id as 
			// a delete and look in bib_records for a record corresponding to this one, so we can mark it deleted
			// If we have no such record, all is well, continue.
			statsService.notifyEvent("DroppedTitle",source.getSourceSystem().getCode());
			// log.warn("Record {} with empty title - bailing",source);
			return Mono.empty();
		}

		// Check if existing...
		return Mono.just(source)
				.flatMap(this::getOrSeed)
				.flatMap((final BibRecord bib) -> {
					final List<ProcessingStep> pipeline = new ArrayList<>();
					pipeline.add(this::step1);
					return Flux.fromIterable(pipeline).reduce(bib, (theBib, step) -> step.apply(bib, source));
				})
		.flatMap(this::saveOrUpdate).flatMap(savedBib -> this.saveIdentifiers(savedBib, source))
		.flatMap( finalBib -> this.updateStatistics(finalBib, source, start_time) );
	}

	public Publisher<Void> cleanup() {
		return bibRepo.cleanUp();
	}

	public Publisher<Void> commit() {
		return bibRepo.commit();
	}

}

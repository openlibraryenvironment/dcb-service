package org.olf.reshare.dcb.core;

import jakarta.inject.Singleton;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.BibIdentifier;
import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.ingest.model.Identifier;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.processing.ProcessingStep;
import org.olf.reshare.dcb.storage.BibIdentifierRepository;
import org.olf.reshare.dcb.storage.BibRepository;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.olf.reshare.dcb.utils.DCBStringUtilities.uuid5ForIdentifier;
import static org.olf.reshare.dcb.utils.DCBStringUtilities.generateBlockingString;

@Singleton
public class BibRecordService {

    private static final Logger log = LoggerFactory.getLogger(BibRecordService.class);

    private final BibRepository bibRepo;

    private final BibIdentifierRepository bibIdentifierRepo;
    private final ClusterRecordRepository clusterRepo;

    BibRecordService(BibRepository bibRepo,
                     BibIdentifierRepository bibIdentifierRepository,
                     ClusterRecordRepository clusterRepo) {
        this.bibRepo = bibRepo;
        this.bibIdentifierRepo = bibIdentifierRepository;
        this.clusterRepo = clusterRepo;
    }

    private BibRecord step1(final BibRecord bib, final IngestRecord imported) {
        // log.info("Executing step 1");
        return bib;
    }

    private BibRecord minimalRecord(final IngestRecord imported) {

        return BibRecord.builder()
                .id(imported.getUuid())
                .title(imported.getTitle())
                .sourceSystemId(imported.getSourceSystem().getId())
                .sourceRecordId(imported.getSourceRecordId())
                .recordStatus(imported.getRecordStatus())
                .typeOfRecord(imported.getTypeOfRecord())
                .derivedType(imported.getDerivedType())
                .blockingTitle(generateBlockingString(imported.getTitle()))
                .canonicalMetadata(imported.getCanonicalMetadata())
                .build();
    }

    public Mono<BibRecord> saveOrUpdate(final BibRecord record) {
        return Mono.from(bibRepo.existsById(record.getId()))
                .flatMap(exists -> Mono.fromDirect(exists ? bibRepo.update(record) : bibRepo.save(record)));
    }

    // https://github.com/micronaut-projects/micronaut-data/discussions/1405
    @Transactional
    public Mono<BibRecord> getOrSeed(final IngestRecord source) {
        return Mono.fromDirect( bibRepo.findById(source.getUuid()) )
                .defaultIfEmpty( minimalRecord(source) )
                .zipWith(Mono.from(clusterRepo.findOneById(source.getClusterRecordId())))
                .flatMap(TupleUtils.function(( bib_record, cluster_record ) -> {
                    return Mono.just(bib_record.setContributesTo(cluster_record));
                }));
    }

    public Flux<BibRecord> findAllByContributesTo(final ClusterRecord clusterRecord) {
        return Flux.from(bibRepo.findAllByContributesTo(clusterRecord));
    }

    public Mono<ClusterRecord> getClusterRecordForBib( UUID bibId ) {
        return Mono.from( bibRepo.findContributesToById(bibId) );
    }

    private Mono<BibRecord> saveIdentifiers(BibRecord savedBib, IngestRecord source) {
        return Flux.fromIterable(source.getIdentifiers())
                .map(id -> IngestRecordIdentifierToModel(id, savedBib))
                .flatMap(this::saveOrUpdateIdentifier)
                .then(Mono.just(savedBib));
    }

    private Mono<BibIdentifier> saveOrUpdateIdentifier(BibIdentifier bibIdentifier) {
        // log.debug("saveOrupdateIdentifier {} {}",bibIdentifier,bibIdentifier.getId());
        return Mono.from(bibIdentifierRepo.existsById(bibIdentifier.getId()))
                .flatMap(exists -> Mono.fromDirect(exists ? bibIdentifierRepo.update(bibIdentifier) : bibIdentifierRepo.save(bibIdentifier)));
    }

    private BibIdentifier IngestRecordIdentifierToModel(Identifier id, BibRecord owner) {
        return BibIdentifier
                .builder()
                .id(uuid5ForIdentifier(id.getNamespace(),id.getValue(),owner.getId()))
                .owner(owner)
                .value( id.getValue() != null ? id.getValue().substring(0, Math.min(id.getValue().length(), 254)) : null)
                .namespace( id.getNamespace() != null ? id.getNamespace().substring(0, Math.min(id.getNamespace().length(), 254)) : null )
                .build();
    }

    @Transactional
    public Publisher<BibRecord> process(final IngestRecord source) {

        // log.debug("BibRecordService::process(...clusterid={})",source.getClusterRecordId());

        // Check if existing...
        return Mono.just(source)
                .flatMap(this::getOrSeed)
                .flatMap((final BibRecord bib) -> {
                    final List<ProcessingStep> pipeline = new ArrayList<>();
                    pipeline.add(this::step1);
                    return Flux.fromIterable(pipeline).reduce(bib, (theBib, step) -> step.apply(bib, source));
                })
                .flatMap(this::saveOrUpdate)
                .flatMap(savedBib -> this.saveIdentifiers(savedBib, source))
                ;
    }

    public Publisher<Void> cleanup() {
        return bibRepo.cleanUp();
    }

    public Publisher<Void> commit() {
        return bibRepo.commit();
    }

}

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

    public static final int PROCESS_VERSION = 1;

    BibRecordService(BibRepository bibRepo,
                     BibIdentifierRepository bibIdentifierRepository,
                     ClusterRecordRepository clusterRepo) {
        this.bibRepo = bibRepo;
        this.bibIdentifierRepo = bibIdentifierRepository;
        this.clusterRepo = clusterRepo;
    }

    private BibRecord step1(final BibRecord bib, final IngestRecord imported) {
        // log.info("Executing step 1");
        bib.setProcessVersion(PROCESS_VERSION);
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
                .metadataScore(imported.getMetadataScore())
                .build();
    }

    public Mono<BibRecord> saveOrUpdate(final BibRecord record) {
        return Mono.from(bibRepo.existsById(record.getId()))
                .flatMap(exists -> Mono.fromDirect(exists ? bibRepo.update(record) : bibRepo.save(record)));
    }

    // Set the selected bib on the cluster to the bib with the highest metadata score,
    private Mono<BibRecord> freshenClusterFor(BibRecord br) {
			UUID cluster_uuid = br.getContributesTo().getId();
			// log.debug("freshenCluster {}",cluster_uuid);
      return Mono.from(bibRepo.findFirstBibRecordInClusterByHighestScore(cluster_uuid))
                .flatMap( best_bib -> {
                        // log.debug("got best bib {}",best_bib);
                        br.getContributesTo().setSelectedBib(best_bib.getId());
                        return Mono.from(clusterRepo.saveOrUpdate(br.getContributesTo()));
                })
				.thenReturn(br);
    }

    private Mono<BibRecord> addBibToCluster(ClusterRecord cr, BibRecord br) {
      // log.debug("addBibToCluster {} {}",cr.getId(), br.getId());
      return Mono.just(br.setContributesTo(cr))
                .thenReturn(br);
    }

    // https://github.com/micronaut-projects/micronaut-data/discussions/1405
    @Transactional
    public Mono<BibRecord> getOrSeed(final IngestRecord source) {
        return Mono.fromDirect( bibRepo.findById(source.getUuid()) )
                .defaultIfEmpty( minimalRecord(source) )
                .zipWith(Mono.from(clusterRepo.findOneById(source.getClusterRecordId())))
                .flatMap(TupleUtils.function(( bib_record, cluster_record ) -> {
                    // return Mono.just(bib_record.setContributesTo(cluster_record));
                    return addBibToCluster(cluster_record, bib_record);
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
								// We have to wait until the bib is saved to be able to see if it has the highest metadata score
								// it may be that it's way more efficient to do this outside the main ingest thread... this is likely
								// the heaviest operation in the ingest pipeline now as it needs to do a query over all bibs in the
								// cluster. MAybe adding the score to that index will make the query resolvable from index only
								.flatMap(savedBib -> this.freshenClusterFor(savedBib))
                ;
    }

    public Publisher<Void> cleanup() {
        return bibRepo.cleanUp();
    }

    public Publisher<Void> commit() {
        return bibRepo.commit();
    }

}

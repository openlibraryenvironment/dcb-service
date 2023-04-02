package org.olf.reshare.dcb.core;

import java.util.UUID;

import javax.transaction.Transactional;

// import javax.transaction.Transactional.TxType;
// @Transactional(value=TxType.REQUIRES_NEW)

import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class RecordClusteringService {

        private static final Logger log = LoggerFactory.getLogger(RecordClusteringService.class);

	
	final ClusterRecordRepository clusterRecords;
	final BibRecordService bibRecords;
	
	public RecordClusteringService(ClusterRecordRepository clusterRecordRepository, BibRecordService bibRecordService) {
		this.clusterRecords = clusterRecordRepository;
		this.bibRecords = bibRecordService;
	}
	
	// Get cluster record by id
	public Mono<ClusterRecord> findById( UUID id ) {
		return Mono.from( clusterRecords.findOneById(id) );
	}
	
	// Add Bib to cluster record
	public Mono<BibRecord> addBibToClusterRecord(BibRecord bib, ClusterRecord clusterRecord) {
		return Mono.just(bib.setContributesTo(clusterRecord))
			.flatMap(bibRecords::saveOrUpdate);
	}
	
	// Get all bibs for cluster record id
	public Flux<BibRecord> findBibsByClusterRecord(ClusterRecord clusterRecord) {
		return Flux.from(bibRecords.findAllByContributesTo(clusterRecord));
	}
	
        @Transactional
	public Mono<ClusterRecord> getOrSeedClusterRecord( IngestRecord ingestRecord ) {

		// log.debug("getOrSeedClusterRecord "+ingestRecord.getUuid());
		
		return Mono.justOrEmpty(ingestRecord.getUuid())
			.flatMap( bibRecords::getClusterRecordForBib )
			.defaultIfEmpty(
					ClusterRecord.builder()
						.id(UUID.randomUUID())
						.title(ingestRecord.getTitle())
						.build())
			.map( clusterRecords::saveOrUpdate )
			.flatMap( Mono::from )
		;
	}
	
	// Generate keys
}

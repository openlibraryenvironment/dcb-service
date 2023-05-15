package org.olf.reshare.dcb.core;

import java.util.Optional;
import java.util.UUID;

import javax.transaction.Transactional;

// import javax.transaction.Transactional.TxType;
// @Transactional(value=TxType.REQUIRES_NEW)

import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.ingest.model.Identifier;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.storage.BibRepository;
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
	final BibRepository bibRepository;
	final BibRecordService bibRecords;
	
	public RecordClusteringService(ClusterRecordRepository clusterRecordRepository, BibRepository bibRepository, BibRecordService bibRecordService) {
		this.clusterRecords = clusterRecordRepository;
		this.bibRepository = bibRepository;
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

	// Placeholder to validate switchIfEmptyApproach below
	Mono<ClusterRecord>  attemptClusterOnIdentifier(IngestRecord ingestRecord, String namespace) {
		Optional<Identifier> identifier = ingestRecord
			.getIdentifiers()
			.stream()
			.filter(id -> id.getNamespace() == namespace)
			.findFirst();

		Mono<ClusterRecord> identified_cluster = Mono.empty();
		// String blocking_title = null;
		if ( identifier.isPresent()) {
			String identifier_str = identifier.get().getValue();
			if ( identifier_str != null)
				identified_cluster = Mono.from(bibRepository.findContributesToIdAndNS(identifier_str, namespace))
							.flatMap( (ClusterRecord cr) -> {
								ingestRecord.setClusterReason("Match cluster "+cr.getId()+" on "+namespace+ " with" +
									identifier_str.substring(0, Math.min(identifier_str.length(), 30)));
								return Mono.just(cr);
							});

		}
		return identified_cluster;

	}

	Mono<ClusterRecord> attemptClusterOnBlockingTitle(IngestRecord ingestRecord) {
		Optional<Identifier> blocking_title_identifier = ingestRecord
			.getIdentifiers()
			.stream()
			.filter(id -> id.getNamespace() == "BLOCKING_TITLE")
			.findFirst();

		Mono<ClusterRecord> identified_cluster = Mono.empty();
		// String blocking_title = null;
		if ( blocking_title_identifier.isPresent()) {
			String blocking_title_str = blocking_title_identifier.get().getValue();
			// log.debug("Cluster this bib using the blocking title {}",blocking_title_str);
			if ( blocking_title_str != null)
				identified_cluster = Mono.from(bibRepository.findContributesToByBlockingTitle(blocking_title_str));
		}
		return identified_cluster;
	}
	
        @Transactional
	public Mono<ClusterRecord> getOrSeedClusterRecord( IngestRecord ingestRecord ) {

		/* ingestRecord now has BLOCKING_TITLE and GOLDRUSH addied to BibIdentifier
			 Deduplication priorities:
			 	 1) If there is an explicit same-as mapping, use it
			 	 2) If there are multiple records with the same OCLC number or LCCN put them in the same cluster
			 	 3) ... Start experimenting
			 	   3a) Put records with the same goldrush ID in the same cluster
			 	   3b) Apply some rules to records with a matching blocking title
			 	 Intent is to use
        	.switchIfEmpty(() -> method2(identifier))
         pattern to
		 */
					// .switchIfEmpty( attemptClusterOnBlockingTitle(ingestRecord) )

		return attemptClusterOnIdentifier(ingestRecord,"OCOLC")
			.switchIfEmpty( attemptClusterOnIdentifier(ingestRecord,"OCLC") )
			.switchIfEmpty( attemptClusterOnIdentifier(ingestRecord,"LCCN") )
			.switchIfEmpty( attemptClusterOnIdentifier(ingestRecord,"GOLDRUSH") )
			.switchIfEmpty( attemptClusterOnIdentifier(ingestRecord,"BLOCKING_TITLE") )
			.defaultIfEmpty(
					ClusterRecord.builder()
						.id(UUID.randomUUID())
						.title(ingestRecord.getTitle())
						.selectedBib(ingestRecord.getUuid())
						.build())
			.map( clusterRecords::saveOrUpdate )
			.flatMap( Mono::from );

	}

	// Generate keys
}

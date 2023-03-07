package org.olf.reshare.dcb.bib;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.Transactional;

import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.processing.ProcessingStep;
import org.olf.reshare.dcb.storage.BibRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class BibRecordService {

	private static Logger log = LoggerFactory.getLogger(BibRecordService.class);

	private final BibRepository bibRepo;

	BibRecordService(BibRepository bibRepo) {
		this.bibRepo = bibRepo;
	}

	private BibRecord step1(final BibRecord bib, final IngestRecord imported) {
//		log.info("Executing step 1");
		return bib;
	}

	private BibRecord minimalRecord(final IngestRecord imported) {
		
		return BibRecord.builder()
			.id(imported.getUuid())
			.title(imported.getTitle())
                        .sourceSystemId(imported.getSourceSystemId())
                        .sourceRecordId(imported.getSourceRecordId())
			.build();
	}

	public Mono<BibRecord> saveOrUpdate(final BibRecord record) {
		return Mono.from(bibRepo.existsById(record.getId()))
			.flatMap(exists -> Mono.fromDirect(exists ? bibRepo.update(record) : bibRepo.save(record)));
	}

	public Mono<BibRecord> getOrSeed(final IngestRecord source) {
		return Mono.from(bibRepo.findById(source.getUuid()))
			.switchIfEmpty(Mono.just(this.minimalRecord(source)));
	}

	@Transactional
	public Publisher<BibRecord> process(final IngestRecord source) {

		// Check if existing...
		return Mono.just(source)
			.flatMap(this::getOrSeed)
			.flatMap((final BibRecord bib) -> {

				final List<ProcessingStep> pipeline = new ArrayList<>();
				pipeline.add(this::step1);

				return Flux.fromIterable(pipeline)
					.reduce(bib, (theBib, step) -> step.apply(bib, source));
			})
			.flatMap(this::saveOrUpdate);
	}

	public Publisher<Void> cleanup() {
		return bibRepo.cleanUp();
	}

	public Publisher<Void> commit() {
		return bibRepo.commit();
	}

}

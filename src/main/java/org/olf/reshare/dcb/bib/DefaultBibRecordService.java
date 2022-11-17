package org.olf.reshare.dcb.bib;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.transaction.Transactional;

import org.olf.reshare.dcb.ImportedRecord;
import org.olf.reshare.dcb.bib.model.BibRecord;
import org.olf.reshare.dcb.bib.processing.ProcessingStep;
import org.olf.reshare.dcb.storage.BibRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class DefaultBibRecordService implements BibRecordService {

	private static Logger log = LoggerFactory.getLogger(DefaultBibRecordService.class);

	private BibRepository bibRepo;

	DefaultBibRecordService(BibRepository bibRepo) {
		this.bibRepo = bibRepo;
	}
	
	private BibRecord step1(BibRecord bib, ImportedRecord imported) {
		
		return bib.setId(UUID.randomUUID())
			.setTitle(imported.title());
	}
	
	
	@Override
	public Publisher<BibRecord> process( final Publisher<ImportedRecord> source ) {
		
		return Flux.from(source)
				.parallel()
				.flatMap((final var record) -> {
					final List<ProcessingStep> pipeline = new ArrayList<>();
					pipeline.add(this::step1);
					
					return Flux.fromIterable(pipeline)
						.reduce(new BibRecord(), (bib, step) -> step.apply(bib, record));
				})
				.sequential()
				.flatMap(bibRepo::save);
	}

	@Override
	public void cleanup() {
		bibRepo.cleanUp();
	}

	@Override
	public void commit() {
		bibRepo.commit();
	}

}

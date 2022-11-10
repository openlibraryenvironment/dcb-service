package org.olf.reshare.dcb.bib;

import java.util.UUID;

import org.olf.reshare.dcb.ImportedRecord;
import org.olf.reshare.dcb.bib.model.BibRecord;
import org.olf.reshare.dcb.storage.BibRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class DefaultBibRecordService implements BibRecordService {

	private static Logger log = LoggerFactory.getLogger(DefaultBibRecordService.class);

	private BibRepository bibRepo;

	DefaultBibRecordService(BibRepository bibRepo) {
		this.bibRepo = bibRepo;
	}

	@Override
	public void addBibRecord(ImportedRecord record) {

		final BibRecord bib = new BibRecord().setId(UUID.randomUUID().toString()).setTitle(record.title());

		log.debug("Adding bib record for title" + bib.getTitle());
		Mono.from( bibRepo.save(bib) ).subscribe();
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

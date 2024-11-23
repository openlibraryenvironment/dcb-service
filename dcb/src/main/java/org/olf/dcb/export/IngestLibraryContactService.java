package org.olf.dcb.export;

import java.util.List;

import org.olf.dcb.core.model.LibraryContact;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.LibraryContactRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class IngestLibraryContactService {

	private final LibraryContactRepository libraryContactRepository;

	public IngestLibraryContactService(
			LibraryContactRepository libraryContactRepository
	) {
		this.libraryContactRepository = libraryContactRepository;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// Process the location records they want to import
		List<LibraryContact> libraryContacts = siteConfiguration.libraryContacts;
		if ((libraryContacts != null) && !libraryContacts.isEmpty()) {
			Flux.fromIterable(libraryContacts)
				.doOnError(e -> {
					String errorMessage = "Exception while processing library contacts for ingest: " + e.toString();
					log.error(errorMessage, e);
					ingestResult.messages.add(errorMessage);
				})
				.flatMap(libraryContact -> processDataLibraryContact(libraryContact, ingestResult.libraryContacts))
				.blockLast();
		}
	}

	private Mono<LibraryContact> processDataLibraryContact(
		LibraryContact libraryContact,
		ProcessingResult processingResult 
	) {
		return(Mono.from(libraryContactRepository.saveOrUpdate(libraryContact))
			.doOnSuccess(a -> {
				processingResult.success(libraryContact.getId().toString(), libraryContact.getPerson().getId().toString());
			})
			.doOnError(e -> {
				processingResult.failed(libraryContact.getId().toString(), libraryContact.getPerson().getId().toString(), e.toString());
			})
			.then(Mono.just(libraryContact))
		);
	}
}

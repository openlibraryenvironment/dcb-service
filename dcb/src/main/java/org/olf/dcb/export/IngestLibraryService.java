package org.olf.dcb.export;

import java.util.List;

import org.olf.dcb.core.model.Library;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.LibraryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class IngestLibraryService {
	private static final Logger log = LoggerFactory.getLogger(IngestLibraryService.class);
	
	private final LibraryRepository libraryRepository;
	
	public IngestLibraryService(
		LibraryRepository libraryRepository
	) {
		this.libraryRepository = libraryRepository;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// Process the location records they want to import
		List<Library> libraries = siteConfiguration.libraries;
		if ((libraries != null) && !libraries.isEmpty()) {
			Flux.fromIterable(libraries)
				.doOnError(e -> {
					String errorMessage = "Exception while processing libraries for ingest: " + e.toString();
					log.error(errorMessage, e);
					ingestResult.messages.add(errorMessage);
				})
				.flatMap(library -> processDataLibrary(library, ingestResult.libraries))
				.blockLast();
		}
	}

	private Mono<Library> processDataLibrary(
			Library library,
		ProcessingResult processingResult 
	) {
		return(Mono.from(libraryRepository.saveOrUpdate(library))
			.doOnSuccess(a -> {
				processingResult.success(library.getId().toString(), library.getFullName());
			})
			.doOnError(e -> {
				processingResult.failed(library.getId().toString(), library.getFullName(), e.toString());
			})
			.then(Mono.just(library))
		);
	}
}

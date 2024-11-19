package org.olf.dcb.export;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Library;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.LibraryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Singleton
public class ExportLibraryService {
	private static final Logger log = LoggerFactory.getLogger(ExportLibraryService.class);
	
	private final LibraryRepository libraryRepository;
	
	public ExportLibraryService(
		LibraryRepository libraryRepository
	) {
		this.libraryRepository = libraryRepository;
	}

	public void export(
		Collection<String> agencyCodes,
		List<UUID> libraryIds,
		SiteConfiguration siteConfiguration
	) {
		// Process the libraries
		if (!agencyCodes.isEmpty()) {
			Flux.from(libraryRepository.findByAgencyCodes(agencyCodes))
				.doOnError(e -> {
					String errorMessage = "Exception while processing library for export: " + e.toString();
					log.error(errorMessage, e);
					siteConfiguration.errors.add(errorMessage);
				})
				.map((Library library) -> {
					siteConfiguration.libraries.add(library);
					libraryIds.add(library.getId());
					return(library);
				})
				.blockLast();
		}
	}
}

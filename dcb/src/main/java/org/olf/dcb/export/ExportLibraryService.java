package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.Library;
import org.olf.dcb.storage.LibraryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

	public Map<String, Object> export(
		Collection<String> agencyCodes,
		List<UUID> libraryIds,
		Map<String, Object> result,
		List<String> errors
	) {
		// Process the libraries
		if (!agencyCodes.isEmpty()) {
			List<Library> libraries = new ArrayList<Library>();
			result.put("libraries", libraries);
			Flux.from(libraryRepository.findByAgencyCodes(agencyCodes))
				.doOnError(e -> {
					String errorMessage = "Exception while processing library for export: " + e.toString();
					log.error(errorMessage, e);
					errors.add(errorMessage);
				})
				.flatMap(library -> processDataLibrary(library, libraries, libraryIds, errors))
				.blockLast();
		}
		
		return(result);
	}

	private Mono<Library> processDataLibrary(
			Library library,
			List<Library> libraries,
			List<UUID> libraryIds,
			List<String> errors
	) {
		libraries.add(library);
		libraryIds.add(library.getId());
		return(Mono.just(library));
	}
}

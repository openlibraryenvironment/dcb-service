package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.storage.LibraryGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ExportLibraryGroupService {
	private static final Logger log = LoggerFactory.getLogger(ExportLibraryGroupService.class);
	
	private final LibraryGroupRepository libraryGroupRepository;
	
	public ExportLibraryGroupService(
			LibraryGroupRepository libraryGroupRepository
	) {
		this.libraryGroupRepository = libraryGroupRepository;
	}

	public Map<String, Object> export(
		Collection<UUID> libraryIds,
		Map<String, Object> result,
		List<String> errors
	) {
		// Process the libraries
		List<LibraryGroup> libraryGroups = new ArrayList<LibraryGroup>();
		result.put("libraryGroups", libraryGroups);
		if (!libraryIds.isEmpty()) {
			Flux.from(libraryGroupRepository.findByLibraryIds(libraryIds))
				.doOnError(e -> {
					String errorMessage = "Exception while processing library library group for export: " + e.toString();
					log.error(errorMessage, e);
					errors.add(errorMessage);
				})
				.flatMap(libraryGroup -> processDataLibraryGroup(libraryGroup, libraryGroups, errors))
				.blockLast();
		}
		
		return(result);
	}

	private Mono<LibraryGroup> processDataLibraryGroup(
			LibraryGroup libraryGroup,
			List<LibraryGroup> libraryGroups,
			List<String> errors
	) {
		libraryGroups.add(libraryGroup);
		return(Mono.just(libraryGroup));
	}
}

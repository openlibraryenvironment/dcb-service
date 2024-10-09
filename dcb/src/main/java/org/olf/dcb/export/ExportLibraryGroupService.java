package org.olf.dcb.export;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.export.model.SiteConfiguration;
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

	public void export(
		Collection<UUID> libraryIds,
		SiteConfiguration siteConfiguration
	) {
		// Process the libraries
		if (!libraryIds.isEmpty()) {
			Flux.from(libraryGroupRepository.findByLibraryIds(libraryIds))
				.doOnError(e -> {
					String errorMessage = "Exception while processing library library group for export: " + e.toString();
					log.error(errorMessage, e);
					siteConfiguration.errors.add(errorMessage);
				})
				.flatMap(libraryGroup -> processDataLibraryGroup(libraryGroup, siteConfiguration))
				.blockLast();
		}
	}

	private Mono<LibraryGroup> processDataLibraryGroup(
			LibraryGroup libraryGroup,
			SiteConfiguration siteConfiguration
	) {
		siteConfiguration.libraryGroups.add(libraryGroup);
		return(Mono.just(libraryGroup));
	}
}

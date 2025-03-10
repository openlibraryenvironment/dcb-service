package org.olf.dcb.export;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.LibraryGroupRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Singleton
public class ExportLibraryGroupService {
	
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
				.map((LibraryGroup libraryGroup) -> {
					siteConfiguration.libraryGroups.add(libraryGroup);
					return(libraryGroup);
				})
				.blockLast();
		}
	}
}

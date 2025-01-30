package org.olf.dcb.export;

import java.util.List;

import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.LibraryGroupRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class IngestLibraryGroupService {
	
	private final LibraryGroupRepository libraryGroupRepository;
	
	public IngestLibraryGroupService(
			LibraryGroupRepository libraryGroupRepository
	) {
		this.libraryGroupRepository = libraryGroupRepository;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// Process the location records they want to import
		List<LibraryGroup> libraryGroups = siteConfiguration.libraryGroups;
		if ((libraryGroups != null) && !libraryGroups.isEmpty()) {
			Flux.fromIterable(libraryGroups)
				.doOnError(e -> {
					String errorMessage = "Exception while processing library groups for ingest: " + e.toString();
					log.error(errorMessage, e);
					ingestResult.messages.add(errorMessage);
				})
				.flatMap(libraryGroup -> processDataLibraryGroup(libraryGroup, ingestResult.libraryGroups))
				.blockLast();
		}
	}

	private Mono<LibraryGroup> processDataLibraryGroup(
		LibraryGroup libraryGroup,
		ProcessingResult processingResult 
	) {
		return(Mono.from(libraryGroupRepository.saveOrUpdate(libraryGroup))
			.doOnSuccess(a -> {
				processingResult.success(libraryGroup.getId().toString(), libraryGroup.getName());
			})
			.doOnError(e -> {
				processingResult.failed(libraryGroup.getId().toString(), libraryGroup.getName(), e.toString());
			})
			.then(Mono.just(libraryGroup))
		);
	}
}

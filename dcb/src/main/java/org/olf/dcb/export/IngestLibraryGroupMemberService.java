package org.olf.dcb.export;

import java.util.List;

import org.olf.dcb.core.model.LibraryGroupMember;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.LibraryGroupMemberRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class IngestLibraryGroupMemberService {
	
	private final LibraryGroupMemberRepository libraryGroupMemberRepository;
	
	public IngestLibraryGroupMemberService(
			LibraryGroupMemberRepository libraryGroupMemberRepository
	) {
		this.libraryGroupMemberRepository = libraryGroupMemberRepository;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// Process the location records they want to import
		List<LibraryGroupMember> libraryGroupMembers = siteConfiguration.libraryGroupMembers;
		if ((libraryGroupMembers != null) && !libraryGroupMembers.isEmpty()) {
			Flux.fromIterable(libraryGroupMembers)
				.doOnError(e -> {
					String errorMessage = "Exception while processing library group members for ingest: " + e.toString();
					log.error(errorMessage, e);
					ingestResult.messages.add(errorMessage);
				})
				.flatMap(libraryGroupMember -> processDataLibraryGroupMember(libraryGroupMember, ingestResult.libraryGroupMembers))
				.blockLast();
		}
	}

	private Mono<LibraryGroupMember> processDataLibraryGroupMember(
		LibraryGroupMember libraryGroupMember,
		ProcessingResult processingResult 
	) {
		return(Mono.from(libraryGroupMemberRepository.saveOrUpdate(libraryGroupMember))
			.doOnSuccess(a -> {
				processingResult.success(libraryGroupMember.getId().toString(), libraryGroupMember.getLibrary().getId().toString());
			})
			.doOnError(e -> {
				processingResult.failed(libraryGroupMember.getId().toString(), libraryGroupMember.getLibrary().getId().toString(), e.toString());
			})
			.then(Mono.just(libraryGroupMember))
		);
	}
}

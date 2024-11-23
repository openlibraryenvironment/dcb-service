package org.olf.dcb.export;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.LibraryGroupMember;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.LibraryGroupMemberRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Singleton
public class ExportLibraryGroupMemberService {
	
	private final LibraryGroupMemberRepository libraryGroupMemberRepository;
	
	public ExportLibraryGroupMemberService(
			LibraryGroupMemberRepository libraryGroupMemberRepository
	) {
		this.libraryGroupMemberRepository = libraryGroupMemberRepository;
	}

	public void export(
		Collection<UUID> libraryIds,
		SiteConfiguration siteConfiguration
	) {
		// Process the libraries
		if (!libraryIds.isEmpty()) {
			Flux.from(libraryGroupMemberRepository.findByLibraryIds(libraryIds))
				.doOnError(e -> {
					String errorMessage = "Exception while processing library library group member for export: " + e.toString();
					log.error(errorMessage, e);
					siteConfiguration.errors.add(errorMessage);
				})
				.map((LibraryGroupMember libraryGroupMember) -> {
					siteConfiguration.libraryGroupMembers.add(libraryGroupMember);
					return(libraryGroupMember);
				})
				.blockLast();
		}
	}
}

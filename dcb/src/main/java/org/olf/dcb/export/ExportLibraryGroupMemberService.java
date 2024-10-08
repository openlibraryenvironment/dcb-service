package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.LibraryGroupMember;
import org.olf.dcb.storage.LibraryGroupMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ExportLibraryGroupMemberService {
	private static final Logger log = LoggerFactory.getLogger(ExportLibraryGroupMemberService.class);
	
	private final LibraryGroupMemberRepository libraryGroupMemberRepository;
	
	public ExportLibraryGroupMemberService(
			LibraryGroupMemberRepository libraryGroupMemberRepository
	) {
		this.libraryGroupMemberRepository = libraryGroupMemberRepository;
	}

	public Map<String, Object> export(
		Collection<UUID> libraryIds,
		Map<String, Object> result,
		List<String> errors
	) {
		// Process the libraries
		List<LibraryGroupMember> libraryGroupMembers = new ArrayList<LibraryGroupMember>();
		result.put("libraryGroupMembers", libraryGroupMembers);
		if (!libraryIds.isEmpty()) {
			Flux.from(libraryGroupMemberRepository.findByLibraryIds(libraryIds))
				.doOnError(e -> {
					String errorMessage = "Exception while processing library library group member for export: " + e.toString();
					log.error(errorMessage, e);
					errors.add(errorMessage);
				})
				.flatMap(libraryGroupMember -> processDataLibraryGroupMember(libraryGroupMember, libraryGroupMembers, errors))
				.blockLast();
		}
		
		return(result);
	}

	private Mono<LibraryGroupMember> processDataLibraryGroupMember(
			LibraryGroupMember libraryGroupMember,
			List<LibraryGroupMember> libraryGroupMembers,
			List<String> errors
	) {
		libraryGroupMembers.add(libraryGroupMember);
		return(Mono.just(libraryGroupMember));
	}
}

package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.LibraryContact;
import org.olf.dcb.storage.LibraryContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ExportLibraryContactService {
	private static final Logger log = LoggerFactory.getLogger(ExportLibraryContactService.class);
	
	private final LibraryContactRepository libraryContactRepository;
	
	public ExportLibraryContactService(
			LibraryContactRepository libraryContactRepository
	) {
		this.libraryContactRepository = libraryContactRepository;
	}

	public Map<String, Object> export(
		Collection<UUID> libraryIds,
		List<UUID> personIds,
		Map<String, Object> result,
		List<String> errors
	) {
		// Process the library contacts
		List<LibraryContact> libraryContacts = new ArrayList<LibraryContact>();
		result.put("libraryContacts", libraryContacts);
		if (!libraryIds.isEmpty()) {
			Flux.from(libraryContactRepository.findByLibraryIds(libraryIds))
				.doOnError(e -> {
					String errorMessage = "Exception while processing library contact for export: " + e.toString();
					log.error(errorMessage, e);
					errors.add(errorMessage);
				})
				.flatMap(libraryContact -> processDataLibraryContact(libraryContact, libraryContacts, personIds, errors))
				.blockLast();
		}
		
		return(result);
	}

	private Mono<LibraryContact> processDataLibraryContact(
			LibraryContact libraryContact,
			List<LibraryContact> libraryContacts,
			List<UUID> personIds,
			List<String> errors
	) {
		libraryContacts.add(libraryContact);
		personIds.add(libraryContact.getPerson().getId());
		return(Mono.just(libraryContact));
	}
}

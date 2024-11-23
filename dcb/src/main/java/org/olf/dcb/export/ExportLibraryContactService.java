package org.olf.dcb.export;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.LibraryContact;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.LibraryContactRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Singleton
public class ExportLibraryContactService {
	
	private final LibraryContactRepository libraryContactRepository;
	
	public ExportLibraryContactService(
			LibraryContactRepository libraryContactRepository
	) {
		this.libraryContactRepository = libraryContactRepository;
	}

	public void export(
		Collection<UUID> libraryIds,
		List<UUID> personIds,
		SiteConfiguration siteConfiguration
	) {
		// Process the library contacts
		if (!libraryIds.isEmpty()) {
			Flux.from(libraryContactRepository.findByLibraryIds(libraryIds))
				.doOnError(e -> {
					String errorMessage = "Exception while processing library contact for export: " + e.toString();
					log.error(errorMessage, e);
					siteConfiguration.errors.add(errorMessage);
				})
				.map((LibraryContact libraryContact) -> {
					siteConfiguration.libraryContacts.add(libraryContact);
					personIds.add(libraryContact.getPerson().getId());
					return(libraryContact);
				})
				.blockLast();
		}
	}
}

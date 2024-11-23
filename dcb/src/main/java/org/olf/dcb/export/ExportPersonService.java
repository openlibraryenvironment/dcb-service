package org.olf.dcb.export;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Person;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.PersonRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Singleton
public class ExportPersonService {
	
	private final PersonRepository personRepository;
	
	public ExportPersonService(
			PersonRepository personRepository
	) {
		this.personRepository = personRepository;
	}

	public void export(
		Collection<UUID> personIds,
		SiteConfiguration siteConfiguration
	) {
		// Process the library contacts
		if (!personIds.isEmpty()) {
			Flux.from(personRepository.findByIds(personIds))
				.doOnError(e -> {
					String errorMessage = "Exception while processing person for export: " + e.toString();
					log.error(errorMessage, e);
					siteConfiguration.errors.add(errorMessage);
				})
				.map((Person person) -> {
					siteConfiguration.persons.add(person);
					return(person);
				})
				.blockLast();
		}
	}
}

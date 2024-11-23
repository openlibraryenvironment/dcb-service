package org.olf.dcb.export;

import java.util.List;

import org.olf.dcb.core.model.Person;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.PersonRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class IngestPersonService {

	private final PersonRepository personRepository;

	public IngestPersonService(
			PersonRepository personRepository
	) {
		this.personRepository = personRepository;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// Process the location records they want to import
		List<Person> persons = siteConfiguration.persons;
		if ((persons != null) && !persons.isEmpty()) {
			Flux.fromIterable(persons)
				.doOnError(e -> {
					String errorMessage = "Exception while processing persons for ingest: " + e.toString();
					log.error(errorMessage, e);
					ingestResult.messages.add(errorMessage);
				})
				.flatMap(person -> processDataPerson(person, ingestResult.persons))
				.blockLast();
		}
	}

	private Mono<Person> processDataPerson(
		Person person,
		ProcessingResult processingResult 
	) {
		return(Mono.from(personRepository.saveOrUpdate(person))
			.doOnSuccess(a -> {
				processingResult.success(person.getId().toString(), person.getLastName());
			})
			.doOnError(e -> {
				processingResult.failed(person.getId().toString(), person.getLastName(), e.toString());
			})
			.then(Mono.just(person))
		);
	}
}

package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.Person;
import org.olf.dcb.storage.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ExportPersonService {
	private static final Logger log = LoggerFactory.getLogger(ExportPersonService.class);
	
	private final PersonRepository personRepository;
	
	public ExportPersonService(
			PersonRepository personRepository
	) {
		this.personRepository = personRepository;
	}

	public Map<String, Object> export(
		Collection<UUID> personIds,
		Map<String, Object> result,
		List<String> errors
	) {
		// Process the library contacts
		List<Person> persons = new ArrayList<Person>();
		result.put("persons", persons);
		if (!personIds.isEmpty()) {
			Flux.from(personRepository.findByIds(personIds))
				.doOnError(e -> {
					String errorMessage = "Exception while processing person for export: " + e.toString();
					log.error(errorMessage, e);
					errors.add(errorMessage);
				})
				.flatMap(person -> processDataPerson(person, persons, errors))
				.blockLast();
		}
		
		return(result);
	}

	private Mono<Person> processDataPerson(
			Person person,
			List<Person> persons,
			List<String> errors
	) {
		persons.add(person);
		return(Mono.just(person));
	}
}

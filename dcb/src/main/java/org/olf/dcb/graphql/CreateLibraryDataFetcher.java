package org.olf.dcb.graphql;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.LibraryContact;
import org.olf.dcb.core.model.Person;


import org.olf.dcb.storage.LibraryContactRepository;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.PersonRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import services.k_int.utils.UUIDUtils;

@Singleton
public class CreateLibraryDataFetcher implements DataFetcher<CompletableFuture<Library>> {

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);

	private LibraryRepository libraryRepository;
	private PersonRepository personRepository;

	private LibraryContactRepository libraryContactRepository;
	private R2dbcOperations r2dbcOperations;

	public CreateLibraryDataFetcher(LibraryRepository libraryRepository, PersonRepository personRepository, LibraryContactRepository libraryContactRepository, R2dbcOperations r2dbcOperations) {
		this.libraryRepository = libraryRepository;
		this.personRepository = personRepository;
		this.r2dbcOperations = r2dbcOperations;
		this.libraryContactRepository = libraryContactRepository;
	}

	@Override
	public CompletableFuture<Library> get(DataFetchingEnvironment env) {

		Map<String, Object> input_map = env.getArgument("input");

		List<Map<String, Object>> contactsInput = (List<Map<String, Object>>) input_map.get("contacts");
		log.debug("createLibraryDataFetcher {}", input_map);
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		Library input = Library.builder()
			.id(input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null)
			.agencyCode(input_map.get("agencyCode").toString())
			.fullName(input_map.get("fullName").toString())
			.shortName(input_map.get("shortName").toString())
			.abbreviatedName(input_map.get("abbreviatedName").toString())
			.address(input_map.get("address").toString())
			.type(input_map.get("type").toString())
			.longitude(Float.valueOf(input_map.get("longitude").toString()))
			.latitude(Float.valueOf(input_map.get("latitude").toString()))
			.supportHours(input_map.get("supportHours").toString())
			.backupDowntimeSchedule(input_map.get("backupDowntimeSchedule").toString())
			.discoverySystem(input_map.get("discoverySystem").toString())
			.patronWebsite(input_map.get("patronWebsite").toString())
			.hostLmsConfiguration(input_map.get("hostLmsConfiguration").toString())
			.reason("Adding a new library")
			.changeCategory("New member")
			.lastEditedBy(userString)
			.build();


		log.debug("getCreateLibraryDataFetcher {}/{}", input_map, input);

		if (input.getId() == null) {
			input.setId(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Library:" + input.getAgencyCode()));
		} else {
			log.debug("update existing");
		}

		log.debug("save or update library {}", input);

		// Save the library first
		return Mono.from(r2dbcOperations.withTransaction(status -> Mono.from(libraryRepository.saveOrUpdate(input))))
			.flatMap(savedLibrary -> {
				// Save the contacts and associate them with the saved Library entity
				Mono<? extends List<? extends Person>> contactsMono = Flux.fromIterable(contactsInput)
					.map(this::createPersonFromInput)
					.flatMap(person -> Mono.from(personRepository.saveOrUpdate(person)))
					.collectList();

				return contactsMono.flatMap(contacts -> {
					return associateContactsWithLibrary(savedLibrary, (List<Person>) contacts)
						.then(Mono.just(savedLibrary));
				});
			})
			.toFuture();
	}

	private Person createPersonFromInput(Map<String, Object> contactInput) {
		return Person.builder()
			.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Person:" + contactInput.get("firstName") + contactInput.get("lastName") + contactInput.get("role") + contactInput.get("email")))
			.firstName(contactInput.get("firstName").toString().trim())
			.lastName(contactInput.get("lastName").toString().trim())
			.role(contactInput.get("role").toString())
			.isPrimaryContact(contactInput.get("isPrimaryContact") != null ? Boolean.parseBoolean(contactInput.get("isPrimaryContact").toString()) : null)
			.email(contactInput.get("email").toString().trim())
			.build();
	}

	private Mono<Void> associateContactsWithLibrary(Library library, List<Person> contacts) {
		return Flux.fromIterable(contacts)
			.flatMap(contact -> {
				LibraryContact libraryContact = new LibraryContact();
				libraryContact.setId(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "LibraryContact:" + library.getFullName() + contact.getFirstName() + contact.getLastName() + contact.getRole()));
				libraryContact.setLibrary(library);
				libraryContact.setPerson(contact);
				return Mono.from(libraryContactRepository.saveOrUpdate(libraryContact));
			})
			.then();
	}
}

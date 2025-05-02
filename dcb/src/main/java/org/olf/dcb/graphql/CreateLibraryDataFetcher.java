package org.olf.dcb.graphql;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.LibraryContact;
import org.olf.dcb.core.model.Person;

import org.olf.dcb.core.model.RoleName;
import org.olf.dcb.storage.LibraryContactRepository;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.PersonRepository;
import org.olf.dcb.storage.RoleRepository;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import services.k_int.utils.UUIDUtils;

@Singleton
@Slf4j
public class CreateLibraryDataFetcher implements DataFetcher<CompletableFuture<Library>> {

	private LibraryRepository libraryRepository;
	private LibraryContactRepository libraryContactRepository;
	private PersonRepository personRepository;
	private RoleRepository roleRepository;
	private R2dbcOperations r2dbcOperations;

	public CreateLibraryDataFetcher(LibraryRepository libraryRepository, PersonRepository personRepository,
																	LibraryContactRepository libraryContactRepository, RoleRepository roleRepository,
																	R2dbcOperations r2dbcOperations) {
		this.libraryRepository = libraryRepository;
		this.personRepository = personRepository;
		this.roleRepository = roleRepository;
		this.r2dbcOperations = r2dbcOperations;
		this.libraryContactRepository = libraryContactRepository;
	}

	@Override
	public CompletableFuture<Library> get(DataFetchingEnvironment env) {

		Map<String, Object> input_map = env.getArgument("input");
		Collection<String> roles = env.getGraphQlContext().get("roles");

		List<Map<String, Object>> contactsInput = (List<Map<String, Object>>) input_map.get("contacts");
		log.debug("createLibraryDataFetcher {}", input_map);
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("createConsortiumDataFetcher: Access denied for user {}: user does not have the required role to create a consortium.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to create a consortium.");
		}

		String agencyCode = input_map.containsKey("agencyCode") ?
			input_map.get("agencyCode").toString() : null;
		String fullName = input_map.containsKey("fullName") ?
			input_map.get("fullName").toString() : null;
		String shortName = input_map.containsKey("shortName") ?
			input_map.get("shortName").toString() : null;
		String abbreviatedName = input_map.containsKey("abbreviatedName") ?
			input_map.get("abbreviatedName").toString() : null;
		String address = input_map.containsKey("address") ?
			input_map.get("address").toString() : null;
		String type = input_map.containsKey("type") ?
			input_map.get("type").toString() : null;

		Float latitude = input_map.containsKey("latitude") ?
			Float.valueOf(input_map.get("longitude").toString()): null;
		Float longitude = input_map.containsKey("longitude") ?
			Float.valueOf(input_map.get("longitude").toString()) : null;

		String backupDowntimeSchedule = input_map.containsKey("backupDowntimeSchedule") ?
			(input_map.get("backupDowntimeSchedule").toString()) : null;
		String supportHours = input_map.containsKey("supportHours") ?
			input_map.get("supportHours").toString() : null;
		String discoverySystem = input_map.containsKey("discoverySystem") ?
			input_map.get("discoverySystem").toString() : null;
		String patronWebsite = input_map.containsKey("patronWebsite") ?
			input_map.get("patronWebsite").toString() : null;
		String hostLmsConfiguration = input_map.containsKey("hostLmsConfiguration") ?
			input_map.get("hostLmsConfiguration").toString() : null;

		String targetLoanToBorrowRatio = input_map.containsKey("targetLoanToBorrowRatio") ?
			input_map.get("targetLoanToBorrowRatio").toString() : null;

		Library input = Library.builder()
			.id(input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null)
			.agencyCode(agencyCode)
			.fullName(fullName)
			.shortName(shortName)
			.abbreviatedName(abbreviatedName)
			.address(address)
			.type(type)
			.longitude(longitude)
			.latitude(latitude)
			.supportHours(supportHours)
			.backupDowntimeSchedule(backupDowntimeSchedule)
			.discoverySystem(discoverySystem)
			.patronWebsite(patronWebsite)
			.hostLmsConfiguration(hostLmsConfiguration)
			.targetLoanToBorrowRatio(targetLoanToBorrowRatio)
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
				// Then associate contacts for the library.
				Mono<? extends List<? extends Person>> contactsMono = Flux.fromIterable(contactsInput)
					.flatMap(contactInput -> createPersonFromInput(contactInput, userString)
						.flatMap(person -> Mono.from(personRepository.saveOrUpdate(person))))
					.collectList();
				return contactsMono.flatMap(contacts -> {
					return associateContactsWithLibrary(savedLibrary, (List<Person>) contacts)
						.then(Mono.just(savedLibrary));
				});
			})
			.toFuture();
	}
	private Mono<Person> createPersonFromInput(Map<String, Object> contactInput, String username) {
		String roleString = contactInput.get("role").toString().trim().replace("-", "_");
		// First validate the role name.
		String newRoleString = roleString.replace(" ", "_").toUpperCase();
		log.debug("RoleName: {}", RoleName.valueOf(newRoleString));
		if (!RoleName.isValid(newRoleString)) {
			return Mono.error(new IllegalArgumentException(
				String.format("Invalid role: '%s'. The roles currently available are: %s",
					roleString,
					RoleName.getValidNames())
			));
		}
		// Next, look up our role entity so we can get all role info.
		RoleName roleName = RoleName.valueOf(newRoleString);
		log.debug("RoleName: {}", roleName);
		String[] validRoles = new String[]{RoleName.getValidNames()};
		return Mono.from(roleRepository.findByName(roleName))
			.switchIfEmpty(Mono.error(new IllegalArgumentException(
				String.format("The contact you provided had a role that was not found in the system: %s. Valid roles are : %s", roleName, Arrays.toString(validRoles)))))
			.map(role -> {
				log.debug("createLibraryDataFetcher: Creating person with role: {}", role.getName());
				return Person.builder()
					.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
						"Person:" + contactInput.get("firstName") + contactInput.get("lastName") +
							role.getName() + contactInput.get("email")))
					.firstName(contactInput.get("firstName").toString().trim())
					.lastName(contactInput.get("lastName").toString().trim())
					.role(role)
					.isPrimaryContact(contactInput.get("isPrimaryContact") != null ?
						Boolean.parseBoolean(contactInput.get("isPrimaryContact").toString()) : null)
					.email(contactInput.get("email").toString().trim())
					.lastEditedBy(username)
					.reason("Add initial contacts for library")
					.changeCategory("New member")
					.build();
			});
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

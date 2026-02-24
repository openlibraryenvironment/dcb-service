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
import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.LibraryContact;
import org.olf.dcb.core.model.Person;

import org.olf.dcb.core.model.RoleName;
import org.olf.dcb.storage.*;

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

	private final LibraryRepository libraryRepository;
	private final LibraryContactRepository libraryContactRepository;
	private final PersonRepository personRepository;
	private final RoleRepository roleRepository;
	private final AgencyRepository agencyRepository;
	private final HostLmsRepository hostLmsRepository;
	private final R2dbcOperations r2dbcOperations;

	public CreateLibraryDataFetcher(LibraryRepository libraryRepository,
																	PersonRepository personRepository,
																	LibraryContactRepository libraryContactRepository,
																	RoleRepository roleRepository,
																	AgencyRepository agencyRepository,
																	HostLmsRepository hostLmsRepository,
																	R2dbcOperations r2dbcOperations) {
		this.libraryRepository = libraryRepository;
		this.personRepository = personRepository;
		this.roleRepository = roleRepository;
		this.agencyRepository = agencyRepository;
		this.hostLmsRepository = hostLmsRepository;
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
			log.warn("createLibraryDataFetcher: Access denied for user {}: user does not have the required role to create a library.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to create a library.");
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
			Float.valueOf(input_map.get("latitude").toString()) : null;
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

		Boolean isSupplyingAgency = input_map.containsKey("isSupplyingAgency") ?
			Boolean.valueOf(input_map.get("isSupplyingAgency").toString()) : null;
		Boolean isBorrowingAgency = input_map.containsKey("isBorrowingAgency") ?
			Boolean.valueOf(input_map.get("isBorrowingAgency").toString()) : null;
		Integer maxLoansInput = input_map.containsKey("maxConsortialLoans") ?
			Integer.parseInt(input_map.get("maxConsortialLoans").toString()): null;
		String authProfile = input_map.containsKey("authProfile") ?
			input_map.get("authProfile").toString() : null;
		String hostLmsCode = input_map.containsKey("hostLmsCode") ?
			input_map.get("hostLmsCode").toString() : null;

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
		if (hostLmsCode == null) {
			log.warn("createLibraryDataFetcher: You must provide a Host LMS code to create a library.");
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Please provide a valid Host LMS code.");
		}

		// Because we have both libraries and agencies to contend with, we need to create both a library AND its corresponding agency
		// If the agency does not exist
		// When we rationalise this, that won't be needed.
		return Mono.from(r2dbcOperations.withTransaction(status ->
				// Get the Host LMS - fail if not present
				Mono.from(hostLmsRepository.findByCode(hostLmsCode))
					.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid Host LMS Code: " + hostLmsCode)))
					.flatMap(hostLms -> {
						return Mono.from(agencyRepository.findOneByCode(agencyCode))
							.flatMap(existingAgency -> {
								boolean agencyUpdated = false;
								// If there is an existing agency, update LMS link
								if (existingAgency.getHostLms() == null || !existingAgency.getHostLms().getId().equals(hostLms.getId())) {
									existingAgency.setHostLms(hostLms);
									agencyUpdated = true;
								}

								// Standardise lat / longs, taking them from the agency
								if (existingAgency.getLatitude() != null) {
									input.setLatitude(existingAgency.getLatitude().floatValue());
								}
								if (existingAgency.getLongitude() != null) {
									input.setLongitude(existingAgency.getLongitude().floatValue());
								}

								if (isSupplyingAgency != null && !isSupplyingAgency.equals(existingAgency.getIsSupplyingAgency())) {
									existingAgency.setIsSupplyingAgency(isSupplyingAgency);
									agencyUpdated = true;
								}
								if (isBorrowingAgency != null && !isBorrowingAgency.equals(existingAgency.getIsBorrowingAgency())) {
									existingAgency.setIsBorrowingAgency(isBorrowingAgency);
									agencyUpdated = true;
								}

								return agencyUpdated ? Mono.from(agencyRepository.saveOrUpdate(existingAgency)) : Mono.just(existingAgency);
							})
							.switchIfEmpty(Mono.defer(() -> {
								// Create new Agency with the LMS we found
								DataAgency newAgency = Agency.builder()
									.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Agency:" + agencyCode))
									.code(agencyCode)
									.name(fullName)
									.hostLms(hostLms)
									.latitude(latitude != null ? latitude.doubleValue() : null)
									.longitude(longitude != null ? longitude.doubleValue() : null)
									.isSupplyingAgency(isSupplyingAgency != null ? isSupplyingAgency : Boolean.FALSE)
									.isBorrowingAgency(isBorrowingAgency != null ? isBorrowingAgency : Boolean.FALSE)
									.authProfile(authProfile)
									.maxConsortialLoans(maxLoansInput)
									.build();
								return Mono.from(agencyRepository.saveOrUpdate(newAgency));
							}));
					})
					.flatMap(agency -> {
						// Link agency to library
						input.setAgency(agency);
						return Mono.from(libraryRepository.saveOrUpdate(input));
					})
					.flatMap(savedLibrary -> {
						// 4. Associate contacts
						Mono<? extends List<? extends Person>> contactsMono = Flux.fromIterable(contactsInput)
							.flatMap(contactInput -> createPersonFromInput(contactInput, userString)
								.flatMap(person -> Mono.from(personRepository.saveOrUpdate(person))))
							.collectList();
						return contactsMono.flatMap(contacts -> {
							return associateContactsWithLibrary(savedLibrary, (List<Person>) contacts)
								.then(Mono.just(savedLibrary));
						});
					})
			))
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

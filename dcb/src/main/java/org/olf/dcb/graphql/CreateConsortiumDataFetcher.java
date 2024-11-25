package org.olf.dcb.graphql;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.api.exceptions.ConsortiumCreationException;

import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.core.model.ConsortiumContact;
import org.olf.dcb.core.model.ConsortiumFunctionalSetting;
import org.olf.dcb.core.model.FunctionalSetting;
import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.core.model.Person;
import org.olf.dcb.core.model.RoleName;

import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.ConsortiumContactRepository;
import org.olf.dcb.storage.ConsortiumFunctionalSettingRepository;
import org.olf.dcb.storage.FunctionalSettingRepository;
import org.olf.dcb.storage.LibraryGroupRepository;
import org.olf.dcb.storage.PersonRepository;
import org.olf.dcb.storage.RoleRepository;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

@Singleton
@Slf4j
public class CreateConsortiumDataFetcher implements DataFetcher<CompletableFuture<Consortium>> {

	private ConsortiumRepository consortiumRepository;
	private LibraryGroupRepository libraryGroupRepository;
	private ConsortiumContactRepository consortiumContactRepository;
	private PersonRepository personRepository;
	private FunctionalSettingRepository functionalSettingRepository;
	private RoleRepository roleRepository;
	private ConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository;
	private R2dbcOperations r2dbcOperations;

	public CreateConsortiumDataFetcher(ConsortiumRepository consortiumRepostory, LibraryGroupRepository libraryGroupRepository,
																		 ConsortiumContactRepository consortiumContactRepository, PersonRepository personRepository,
																		 ConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository,
																		 FunctionalSettingRepository functionalSettingRepository, RoleRepository roleRepository,
																		 R2dbcOperations r2dbcOperations) {
		this.consortiumRepository = consortiumRepostory;
		this.libraryGroupRepository = libraryGroupRepository;
		this.consortiumContactRepository = consortiumContactRepository;
		this.consortiumFunctionalSettingRepository = consortiumFunctionalSettingRepository;
		this.personRepository = personRepository;
		this.roleRepository = roleRepository;
		this.functionalSettingRepository = functionalSettingRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	@SuppressWarnings("unchecked")
	public CompletableFuture<Consortium> get(DataFetchingEnvironment env) {
		Map input_map = env.getArgument("input");
		List<Map<String, Object>> functionalSettingsInput = (List<Map<String, Object>>) input_map.get("functionalSettings");
		List<Map<String, Object>> contactsInput = (List<Map<String, Object>>) input_map.get("contacts");
		Collection<String> roles = env.getGraphQlContext().get("roles");
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");
		Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString);
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString);

		// Pre-requisite: There must already be a LibraryGroup that we want to associate to this Consortium
		// It will have a one-to-one relationship (as long as the type is "consortium" - otherwise no relationship)
		// And we must supply the name when we create the Consortium.

		log.debug("createConsortiumDataFetcher {}", input_map);
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("createConsortiumDataFetcher: Access denied for user {}: user does not have the required role to create a consortium.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to create a consortium.");
		}

		return Mono.from(libraryGroupRepository.findOneByNameAndTypeIgnoreCase(input_map.get("groupName").toString(), "Consortium"))
			.flatMap(libraryGroup -> {
				if (libraryGroup != null) {
					// If a library group matching the conditions is found, we can attempt to create the associated consortium
					return Mono.from(consortiumRepository.exists())
						.flatMap(exists -> {
								if (exists) {
									// Do not allow a new consortium to be created for a DCB instance that already has one.
									return Mono.error(new ConsortiumCreationException("A consortium already exists for this DCB system."));
								}
							// Input date must be in format YYYY-MM-DD
								LocalDate launchDate = LocalDate.parse(input_map.get("dateOfLaunch").toString(), DateTimeFormatter.ISO_LOCAL_DATE );

								String displayName = Optional.ofNullable(input_map.get("displayName"))
									.map(Object::toString)
									.orElse("");

								String headerImageUrl = Optional.ofNullable(input_map.get("headerImageUrl"))
									.map(Object::toString)
									.orElse("");

								String aboutImageUrl = Optional.ofNullable(input_map.get("aboutImageUrl"))
									.map(Object::toString)
									.orElse("");

								String catalogueSearchUrl = Optional.ofNullable(input_map.get("catalogueSearchUrl"))
									.map(Object::toString)
									.orElse("");

								String description = Optional.ofNullable(input_map.get("description"))
									.map(Object::toString)
									.orElse("");

								String websiteUrl = Optional.ofNullable(input_map.get("websiteUrl"))
									.map(Object::toString)
									.orElse("");

								Consortium consortium = Consortium.builder()
									.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Consortium:" + input_map.get("name").toString()))
									.name(input_map.get("name").toString())
									.displayName(displayName)
									.headerImageUrl(headerImageUrl)
									.aboutImageUrl(aboutImageUrl)
									.catalogueSearchUrl(catalogueSearchUrl)
									.description(description)
									.websiteUrl(websiteUrl)
									.dateOfLaunch(launchDate)
									.libraryGroup(libraryGroup)
									.lastEditedBy(userString)
									.build();
								changeReferenceUrl.ifPresent(consortium::setChangeReferenceUrl);
								changeCategory.ifPresent(consortium::setChangeCategory);
								reason.ifPresent(consortium::setReason);
								// Save consortium first. Then associate contacts and settings
									return Mono.from(r2dbcOperations.withTransaction(status ->
											Mono.from(consortiumRepository.saveOrUpdate(consortium))))
										.flatMap(savedConsortium -> {
											Mono<? extends List<? extends Person>> contactsMono = Flux.fromIterable(contactsInput)
												.flatMap(contactInput -> createPersonFromInput(contactInput, userString)
													.flatMap(person -> Mono.from(personRepository.saveOrUpdate(person))))
												.collectList();
										Mono<? extends List<? extends FunctionalSetting>> functionalSettingMono = Flux.fromIterable(functionalSettingsInput)
											.map(this::createFunctionalSettingFromInput)
											.flatMap(functionalSetting -> Mono.from(functionalSettingRepository.saveOrUpdate(functionalSetting)))
											.collectList();
										return functionalSettingMono.flatMap(functionalSettings -> {
											return associateFunctionalSettingsWithConsortium(savedConsortium, (List<FunctionalSetting>) functionalSettings)
												.then(contactsMono.flatMap(contacts -> {
													return associateContactsWithConsortium(savedConsortium, (List<Person>) contacts)
														.then(Mono.just(savedConsortium));
												}));
										});
									});
							});
						}
				else {
					// If not, we cannot create a consortium.
					return Mono.error(new ConsortiumCreationException("Consortium creation has failed because a compatible library group was not found. You must supply the name of an existing LibraryGroup of type consortium."));
				}
				}
			).toFuture();
	}

	private Mono<Person> createPersonFromInput(Map<String, Object> contactInput, String username) {
		String roleString = contactInput.get("role").toString().trim().replace("-", "_");
		// First validate the role name. We may need to format it first.
		String newRoleString = roleString.replace(" ", "_").toUpperCase();
		if (!RoleName.isValid(newRoleString)) {
			return Mono.error(new IllegalArgumentException(
				String.format("Invalid role: '%s'. The roles currently available are: %s",
					roleString,
					RoleName.getValidNames())
			));
		}
		// Next, look up our role entity so we can get all role info.
		RoleName roleName = RoleName.valueOf(newRoleString);
		String[] validRoles = new String[]{RoleName.getValidNames()};
		return Mono.from(roleRepository.findByName(roleName))
			.switchIfEmpty(Mono.error(new ConsortiumCreationException(
				String.format("The consortium contact you provided had a role that was not found in the system: %s. Valid roles are : %s", roleString, validRoles))))
			.map(role -> {
				log.debug("createConsortiumDataFetcher: Creating person with role: {}", role.getName());
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
					.build();
			});
	}


	private Mono<Void> associateContactsWithConsortium(Consortium consortium, List<Person> contacts) {
		return Flux.fromIterable(contacts)
			.flatMap(contact -> {
				ConsortiumContact consortiumContact = new ConsortiumContact();
				consortiumContact.setId(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "ConsortiumContact:" + consortium.getName() + contact.getFirstName() + contact.getLastName() + contact.getRole()));
				consortiumContact.setConsortium(consortium);
				consortiumContact.setPerson(contact);
				return Mono.from(consortiumContactRepository.saveOrUpdate(consortiumContact));
			})
			.then();
	}

	private FunctionalSetting createFunctionalSettingFromInput(Map<String, Object> settingInput) {
		String settingName = settingInput.get("name").toString();
		// Check for validity. We use a FunctionalSettingType enum for this.
		if (!FunctionalSettingType.isValid(settingName)) {
			throw new IllegalArgumentException(
				String.format("Invalid functional setting name: '%s'. The functional settings currently available are: %s",
					settingName,
					FunctionalSettingType.getValidNames())
			);
		}
		FunctionalSettingType type = FunctionalSettingType.valueOf(settingInput.get("name").toString());
		log.debug("createConsortiumDataFetcher: Functional setting created of type: {}", type);
		return FunctionalSetting.builder()
			.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "FunctionalSetting:" + settingInput.get("name").toString()))
			.name(type)
			.enabled(Boolean.valueOf(settingInput.get("enabled").toString()))
			.description(settingInput.get("description").toString())
			.build();
	}

	private Mono<Void> associateFunctionalSettingsWithConsortium(Consortium consortium, List<FunctionalSetting> functionalSettings) {
		return Flux.fromIterable(functionalSettings)
			.flatMap(functionalSetting -> {
				ConsortiumFunctionalSetting consortiumFunctionalSetting = new ConsortiumFunctionalSetting();
				consortiumFunctionalSetting.setId(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "ConsortiumFunctionalSetting:" + functionalSetting.getName() + "consortium: {}"+consortium.getName()));
				consortiumFunctionalSetting.setConsortium(consortium);
				consortiumFunctionalSetting.setFunctionalSetting(functionalSetting);
				return Mono.from(consortiumFunctionalSettingRepository.saveOrUpdate(consortiumFunctionalSetting));
			})
			.then();
	}

}

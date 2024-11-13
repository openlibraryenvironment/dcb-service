package org.olf.dcb.graphql;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.api.exceptions.ConsortiumCreationException;
import org.olf.dcb.core.model.FunctionalSetting;
import org.olf.dcb.core.model.ConsortiumFunctionalSetting;
import org.olf.dcb.core.model.Person;
import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.core.model.ConsortiumContact;




import org.olf.dcb.storage.FunctionalSettingRepository;
import org.olf.dcb.storage.ConsortiumFunctionalSettingRepository;
import org.olf.dcb.storage.ConsortiumContactRepository;
import org.olf.dcb.storage.PersonRepository;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.LibraryGroupRepository;




import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

@Singleton
public class CreateConsortiumDataFetcher implements DataFetcher<CompletableFuture<Consortium>> {

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);

	private ConsortiumRepository consortiumRepository;

	private LibraryGroupRepository libraryGroupRepository;
	private ConsortiumContactRepository consortiumContactRepository;
	private PersonRepository personRepository;

	private FunctionalSettingRepository functionalSettingRepository;


	private ConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository;

	private R2dbcOperations r2dbcOperations;

	public CreateConsortiumDataFetcher(ConsortiumRepository consortiumRepostory, LibraryGroupRepository libraryGroupRepository, ConsortiumContactRepository consortiumContactRepository, PersonRepository personRepository, ConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository, FunctionalSettingRepository functionalSettingRepository, R2dbcOperations r2dbcOperations) {
		this.consortiumRepository = consortiumRepostory;
		this.libraryGroupRepository = libraryGroupRepository;
		this.consortiumContactRepository = consortiumContactRepository;
		this.consortiumFunctionalSettingRepository = consortiumFunctionalSettingRepository;
		this.personRepository = personRepository;
		this.functionalSettingRepository = functionalSettingRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<Consortium> get(DataFetchingEnvironment env) {
		Map input_map = env.getArgument("input");
		List<Map<String, Object>> functionalSettingsInput = (List<Map<String, Object>>) input_map.get("functionalSettings");



		// Pre-requisite: There must already be a LibraryGroup that we want to associate to this Consortium
		// It will have a one-to-one relationship (as long as the type is "consortium" - otherwise no relationship)
		// And we must supply the name when we create the Consortium.

		log.debug("createConsortiumDataFetcher {}", input_map);

		return Mono.from(libraryGroupRepository.findOneByNameAndTypeIgnoreCase(input_map.get("groupName").toString(), "Consortium"))
			.flatMap(libraryGroup -> {
				if (libraryGroup != null) {
					// If a library group matching the conditions is found, we can create the associated consortium
					// Input date must be in format YYYY-MM-DD
					LocalDate launchDate = LocalDate.parse(input_map.get("dateOfLaunch").toString(), DateTimeFormatter.ISO_LOCAL_DATE );
					Consortium consortium = Consortium.builder()
						.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Consortium:" + input_map.get("name").toString()))
						.name(input_map.get("name").toString())
						.dateOfLaunch(launchDate)
						.libraryGroup(libraryGroup).build();
					// Save consortium first. Then associate contacts and settings
					log.debug("STARTING SAVE OF CONSORTIUM");

						return Mono.from(r2dbcOperations.withTransaction(status -> Mono.from(consortiumRepository.saveOrUpdate(consortium))))
							.flatMap(savedConsortium -> {
//								Mono<? extends List<? extends Person>> contactsMono = Flux.fromIterable(contactsInput)
//									.map(this::createPersonFromInput)
//									.flatMap(person -> Mono.from(personRepository.saveOrUpdate(person)))
//									.collectList();
								log.debug("SavedConsortium before mono: {}", savedConsortium);
								Mono<? extends List<? extends FunctionalSetting>> functionalSettingMono = Flux.fromIterable(functionalSettingsInput)
									.map(this::createFunctionalSettingFromInput)
									.flatMap(functionalSetting -> Mono.from(functionalSettingRepository.saveOrUpdate(functionalSetting)).doOnSuccess(functionalSetting1 -> log.debug("FS saved")))
									.collectList().doOnSuccess(functionalSettings -> log.debug("Functional setting list created {}", functionalSettings));

								log.debug("SavedConsortium: {}", savedConsortium);

								return functionalSettingMono.flatMap(functionalSettings -> {
									log.debug("Inside functional settings mono");
									return associateFunctionalSettingsWithConsortium(savedConsortium, (List<FunctionalSetting>) functionalSettings)
										.then(Mono.just(savedConsortium));
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

	private Mono<Void> associateContactsWithConsortium(Consortium consortium, List<Person> contacts) {
		return Flux.fromIterable(contacts)
			.flatMap(contact -> {
				ConsortiumContact consortiumContact = new ConsortiumContact();
				// Think about this ID
				consortiumContact.setId(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "ConsortiumContact:" + consortium.getName() + contact.getFirstName() + contact.getLastName() + contact.getRole()));
				consortiumContact.setConsortium(consortium);
				consortiumContact.setPerson(contact);
				return Mono.from(consortiumContactRepository.saveOrUpdate(consortiumContact));
			})
			.then();
	}

	private FunctionalSetting createFunctionalSettingFromInput(Map<String, Object> settingInput) {
		// Disallow non-valid settings here. The list will need to be maintained.
		log.debug("Creating FS from input");
		return FunctionalSetting.builder()
			.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "FunctionalSetting:" + settingInput.get("name")))
			.name(settingInput.get("name").toString())
			.enabled(Boolean.valueOf(settingInput.get("enabled").toString()))
			.description(settingInput.get("description").toString())
			.build();
	}

	private Mono<Void> associateFunctionalSettingsWithConsortium(Consortium consortium, List<FunctionalSetting> functionalSettings) {
		log.debug("Associating");
		return Flux.fromIterable(functionalSettings)
			.flatMap(functionalSetting -> {
				log.debug("Creating consortium functional setting");
				ConsortiumFunctionalSetting consortiumFunctionalSetting = new ConsortiumFunctionalSetting();
				// Think about this ID
				consortiumFunctionalSetting.setId(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "ConsortiumFunctionalSetting:" + functionalSetting.getName()));
				consortiumFunctionalSetting.setConsortium(consortium);
				consortiumFunctionalSetting.setFunctionalSetting(functionalSetting);
				log.debug("Saving consortium functional setting");
				return Mono.from(consortiumFunctionalSettingRepository.saveOrUpdate(consortiumFunctionalSetting)).doOnSuccess(consortiumFunctionalSetting1 -> log.debug("Successfully saved CFS: {}", consortiumFunctionalSetting1));
			})
			.then();
	}

}


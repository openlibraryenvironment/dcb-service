package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.core.model.ConsortiumFunctionalSetting;
import org.olf.dcb.core.model.FunctionalSetting;
import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.ConsortiumFunctionalSettingRepository;
import org.olf.dcb.storage.FunctionalSettingRepository;

import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

// Creates a functional setting and associates it with a consortium (if provided and not already extant)
// Functional setting must have a valid name - these are provided in FunctionalSettingType.java
// New valid names must be added to that enum before attempting to use this mutation to create their settings.
@Singleton
@Slf4j
public class CreateFunctionalSettingDataFetcher implements DataFetcher<CompletableFuture<FunctionalSetting>> {

	private ConsortiumRepository consortiumRepository;
	private ConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository;
	private FunctionalSettingRepository functionalSettingRepository;
	private R2dbcOperations r2dbcOperations;


	public CreateFunctionalSettingDataFetcher(ConsortiumRepository consortiumRepostory, ConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository, FunctionalSettingRepository functionalSettingRepository, R2dbcOperations r2dbcOperations) {
		this.consortiumRepository = consortiumRepostory;
		this.consortiumFunctionalSettingRepository = consortiumFunctionalSettingRepository;
		this.functionalSettingRepository = functionalSettingRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<FunctionalSetting> get(DataFetchingEnvironment env) {

		// Take input for a functional setting to be created and a consortium name to associate it with.
		// When we add support for library association, this should become optional (as long as one is provided)
		// If the setting already exists it should be associated with the provided consortium.
		Map<String, Object> input_map = env.getArgument("input");
		// Note: these are user access roles from Keycloak
		Collection<String> roles = env.getGraphQlContext().get("roles");
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");
		// Input has functional setting info + consortium name
		Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString);
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString);
		log.debug("createFunctionalSettingDataFetcher {}", input_map);
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("createFunctionalSettingDataFetcher: Access denied for user {}: user does not have the required role to create a functional setting.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to create a functional setting.");
		}
		// Now check the name for validity. If it's valid, we can look it up and check for an existing functional setting.
		// If valid name and no existing setting, create and associate with provided consortium.
		// If valid name, existing setting but not associated with consortium, associate with provided consortium.
		// If valid and associated, return existing
		// If invalid, fail with message.
		// If consortium invalid, error out.

		String settingName = Optional.ofNullable(input_map.get("name"))
			.map(Object::toString)
			.orElseThrow(() -> new IllegalArgumentException("Setting name must be provided"));
		String consortiumName = Optional.ofNullable(input_map.get("consortiumName"))
			.map(Object::toString)
			.orElseThrow(() -> new IllegalArgumentException("Consortium name must be provided"));


		if (!FunctionalSettingType.isValid(settingName)) {
			throw new IllegalArgumentException(
				String.format("Invalid functional setting name: '%s'. The functional settings currently available are: %s",
					settingName,
					FunctionalSettingType.getValidNames())
			);
		}
		// The functional setting name provided is valid. We must now look it up in the functional setting repository.
		FunctionalSettingType settingNameEnum = FunctionalSettingType.valueOf(settingName);

		// First, look for the consortium. If it doesn't exist, we throw an error.
		return Mono.from(consortiumRepository.findOneByName(consortiumName))
			.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
				String.format("Consortium with name %s not found", consortiumName))))
			.flatMap(consortium -> {
				// The consortium exists: now we must determine whether the functional setting already exists or not.
				return Mono.from(functionalSettingRepository.findByName(settingNameEnum))
					.flatMap(existingSetting -> {
						// We have an existing setting for the name provided.
						// Now we must check if the setting is already associated with this consortium
						return Mono.from(consortiumFunctionalSettingRepository
								.findByConsortiumAndFunctionalSetting(consortium, existingSetting))
							  .map(existingAssociation -> {
									log.info("Functional setting {} already exists and is associated with consortium {}",
										settingName, consortiumName);
									return existingSetting;
							})
							.switchIfEmpty(Mono.defer(() -> {
								// The setting is not already associated with this consortium.
								// An association needs to be created.
								return Mono.from(r2dbcOperations.withTransaction(status -> {
									ConsortiumFunctionalSetting newAssociation = new ConsortiumFunctionalSetting();
									newAssociation.setId(UUIDUtils.nameUUIDFromNamespaceAndString(
										NAMESPACE_DCB,
										"ConsortiumFunctionalSetting:" + existingSetting.getName() +
											" consortium: " + consortium.getName()));
									newAssociation.setConsortium(consortium);
									newAssociation.setFunctionalSetting(existingSetting);
									// If the data change log fields were provided, set them now.
									reason.ifPresent(newAssociation::setReason);
									changeReferenceUrl.ifPresent(newAssociation::setChangeReferenceUrl);
									changeCategory.ifPresent(newAssociation::setChangeCategory);
									log.debug("New association has been created for an existing non-associated setting {} {}", newAssociation, existingSetting);
									return Mono.from(consortiumFunctionalSettingRepository.saveOrUpdate(newAssociation))
										.thenReturn(existingSetting);
									// We may want to return something different here
								}));
							}));
					})
					.switchIfEmpty(Mono.defer(() -> {
						// No existing setting with that name - so create a new setting and associate with provided consortium
						return Mono.from(r2dbcOperations.withTransaction(status ->
							Mono.from(functionalSettingRepository.save(createFunctionalSettingFromInput(input_map)))
								.flatMap(savedSetting -> {
									ConsortiumFunctionalSetting newAssociation = new ConsortiumFunctionalSetting();
									newAssociation.setId(UUIDUtils.nameUUIDFromNamespaceAndString(
										NAMESPACE_DCB,
										"ConsortiumFunctionalSetting:" + savedSetting.getName() +
											" consortium: " + consortium.getName()));
									newAssociation.setConsortium(consortium);
									newAssociation.setFunctionalSetting(savedSetting);
									// Set optional data change log fields if provided
									reason.ifPresent(newAssociation::setReason);
									changeReferenceUrl.ifPresent(newAssociation::setChangeReferenceUrl);
									changeCategory.ifPresent(newAssociation::setChangeCategory);
									// Then return the new setting
									return Mono.from(consortiumFunctionalSettingRepository.saveOrUpdate(newAssociation))
										.thenReturn(savedSetting);
								})
						));
					}));
			})
			.toFuture();
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
		// If not provided, return false as functional settings are always off by default.

		Boolean enabled = Optional.ofNullable(settingInput.get("enabled"))
			.map(e -> Boolean.valueOf(e.toString()))
			.orElse(false);

		return FunctionalSetting.builder()
			.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "FunctionalSetting:" + settingInput.get("name").toString()))
			.name(type)
			.enabled(enabled)
			.description(settingInput.get("description").toString())
			.build();
	}

	private Mono<Void> associateFunctionalSettingWithConsortium(Consortium consortium, FunctionalSetting functionalSetting) {
		ConsortiumFunctionalSetting consortiumFunctionalSetting = new ConsortiumFunctionalSetting();
		consortiumFunctionalSetting.setId(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "ConsortiumFunctionalSetting:" + functionalSetting.getName() + "consortium: {}" + consortium.getName()));
		consortiumFunctionalSetting.setConsortium(consortium);
		consortiumFunctionalSetting.setFunctionalSetting(functionalSetting);
		return Mono.from(consortiumFunctionalSettingRepository.saveOrUpdate(consortiumFunctionalSetting)).then();
	}}


package org.olf.dcb.graphql;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.api.exceptions.EntityCreationException;
import org.olf.dcb.core.api.exceptions.MappingCreationException;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Singleton
@Slf4j
public class CreateReferenceValueMappingDataFetcher implements DataFetcher<CompletableFuture<ReferenceValueMapping>> {
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	private final R2dbcOperations r2dbcOperations;

	public CreateReferenceValueMappingDataFetcher(ReferenceValueMappingRepository referenceValueMappingRepository, R2dbcOperations r2dbcOperations) {
		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	private Mono<Void> checkForDuplicateMapping(String fromValue, String fromContext, String toContext,
																							String fromCategory, String toCategory) {

		return Mono.from(referenceValueMappingRepository.findByFromValueAndDeletedFalseAndFromContextAndFromCategory(
				fromValue, fromContext, fromCategory))
			.flatMap(existingMapping -> Mono.<Void>error(new MappingCreationException(
				"A mapping with fromValue '" + fromValue + "' already exists for the given from context and category. Existing mapping ID is"+existingMapping.getId())))
			.switchIfEmpty(Mono.from(referenceValueMappingRepository.findByFromValueAndDeletedFalseAndToContextAndToCategory(
					fromValue, toContext, toCategory))
				.flatMap(existingMapping -> Mono.<Void>error(new MappingCreationException(
					"A mapping with fromValue '" + fromValue + "' already exists for the given to context and category. Existing mapping ID is"+existingMapping.getId())))
				.switchIfEmpty(Mono.empty().then()));
	}


	@Override
	public CompletableFuture<ReferenceValueMapping> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("createReferenceValueMappingDataFetcher {}", input_map);
		if (input_map == null) {
			throw new EntityCreationException("Mapping creation failed: no input was provided.");
		}

		String[] requiredFields = {
			"fromContext", "fromCategory", "fromValue", "toContext",
			"toCategory", "toValue",
		};

		for (String field : requiredFields) {
			String value = Optional.ofNullable(input_map.get(field))
				.map(Object::toString)
				.map(String::trim)
				.orElse("");

			if (value.isEmpty()) {
				throw new EntityCreationException(
					String.format("Mapping creation failed: %s is required and cannot be empty", field));
			}
		}

		// After validation, safely get and trim all values
		String fromContext = Optional.ofNullable(input_map.get("fromContext"))
			.map(Object::toString)
			.map(String::trim)
			.orElse("");
		String fromCategory = Optional.ofNullable(input_map.get("fromCategory"))
			.map(Object::toString)
			.map(String::trim)
			.orElse("");
		String fromValue = Optional.ofNullable(input_map.get("fromValue"))
			.map(Object::toString)
			.map(String::trim)
			.orElse("");
		String toContext = Optional.ofNullable(input_map.get("toContext"))
			.map(Object::toString)
			.map(String::trim)
			.orElse("");
		String toCategory = Optional.ofNullable(input_map.get("toCategory"))
			.map(Object::toString)
			.map(String::trim)
			.orElse("");
		String toValue = Optional.ofNullable(input_map.get("toValue"))
			.map(Object::toString)
			.map(String::trim)
			.orElse("");

		List<String> validPatronTypes = Arrays.asList("ADULT", "CHILD", "FACULTY", "GRADUATE", "NOT_ELIGIBLE", "PATRON", "POSTDOC", "SENIOR", "STAFF", "UNDERGRADUATE", "YOUNG_ADULT");
		List<String> validItemTypes = Arrays.asList("CIRC", "NONCIRC", "CIRCAV");

		// General validation
		if (fromValue.isBlank() || fromContext.isBlank() || fromCategory.isBlank() || toValue.isBlank() || toContext.isBlank() || toCategory.isBlank())
		{
			throw new MappingCreationException("Mapping creation failed: mapping values cannot be blank.");
		}
		if (fromContext.equals(toContext)) {
			throw new MappingCreationException("Mapping creation failed: 'fromContext' and 'toContext' cannot be the same.");
		}
		// Location validation
		if ((fromCategory.equals("Location") || toCategory.equals("Location")) && !toContext.equals("DCB"))
		{
			throw new MappingCreationException("Mapping creation failed: Location mapping must have a toContext value of 'DCB'.");
		}
		// Item type validation
		if ((fromCategory.equals("ItemType") || toCategory.equals("ItemType")) && (fromContext.equals("DCB") || toContext.equals("DCB")))
		{
			if (fromContext.equals("DCB") && !validItemTypes.contains(fromValue))
			{
				log.debug("fromContext: {}, fromValue: {}", fromContext, fromValue);
				throw new MappingCreationException("Mapping creation failed: the from value for this ItemType mapping is not valid. Valid from values for this mapping are"+validItemTypes);
			}
			else if (toContext.equals("DCB") && !validItemTypes.contains(toValue))
			{
				log.debug("ToContext: {}, toValue: {}", toContext, toValue);
				throw new MappingCreationException("Mapping creation failed: the to value for this ItemType mapping is not valid. Valid to values for this mapping are"+validItemTypes);
			}
		}
		// Patron type validation
		if ((fromCategory.equals("patronType") || toCategory.equals("patronType")) && (fromContext.equals("DCB") || toContext.equals("DCB")))
		{
			if (fromContext.equals("DCB") && !validPatronTypes.contains(fromValue))
				throw new MappingCreationException("Mapping creation failed: the from value for this patronType mapping is not valid. Valid from values for this mapping are"+validPatronTypes);
			else if (toContext.equals("DCB") && !validPatronTypes.contains(toValue))
			{
				throw new MappingCreationException("Mapping creation failed: to value for this patronType mapping is not valid. Valid to values for this mapping are"+validPatronTypes);
			}
		}

		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");
		Collection<String> roles = env.getGraphQlContext().get("roles");


		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("createReferenceValueMappingDataFetcher: Access denied for user {}: user does not have the required role to create a reference value mapping.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}


		Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString);
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString);

		ReferenceValueMapping rvm = ReferenceValueMapping.builder()
			.id(UUIDUtils.dnsUUID(fromContext+":"+fromCategory+":"+fromValue+":"+toContext+":"+toCategory))
			.fromContext(fromContext)
			.fromCategory(fromCategory)
			.fromValue(fromValue)
			.toContext(toContext)
			.toCategory(toCategory)
			.toValue(toValue)
			.lastImported(Instant.now())
			.deleted(false)
			.lastEditedBy(userString)
			.build();
		changeReferenceUrl.ifPresent(rvm::setChangeReferenceUrl);
		changeCategory.ifPresent(rvm::setChangeCategory);
		reason.ifPresent(rvm::setReason);

		return Mono.from(r2dbcOperations.withTransaction(status ->
			checkForDuplicateMapping(fromValue, fromContext, toContext, fromCategory, toCategory)
				.then(Mono.from(referenceValueMappingRepository.saveOrUpdate(rvm))))
		).toFuture();
	}
}

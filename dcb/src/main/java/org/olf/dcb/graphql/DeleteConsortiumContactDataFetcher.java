package org.olf.dcb.graphql;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.extern.slf4j.Slf4j;

import org.olf.dcb.core.model.Person;
import org.olf.dcb.storage.ConsortiumContactRepository;
import org.olf.dcb.storage.PersonRepository;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Deletes a consortium contact and associated person object.
 * This operation ensures complete removal of contact data.
 */
@Singleton
@Slf4j
public class DeleteConsortiumContactDataFetcher implements DataFetcher<CompletableFuture<Map<String, Object>>> {

	private final ConsortiumContactRepository consortiumContactRepository;
	private final PersonRepository personRepository;
	private final R2dbcOperations r2dbcOperations;

	public DeleteConsortiumContactDataFetcher(
		ConsortiumContactRepository consortiumContactRepository,
		PersonRepository personRepository,
		R2dbcOperations r2dbcOperations) {

		this.consortiumContactRepository = consortiumContactRepository;
		this.personRepository = personRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<Map<String, Object>> get(DataFetchingEnvironment env) {
		Map<String, Object> input = env.getArgument("input");
		// We can expect this to be the Person ID due to how DCB Admin works
		String idStr = Optional.ofNullable(input.get("personId"))
			.map(Object::toString)
			.orElseThrow(() -> new HttpStatusException(HttpStatus.BAD_REQUEST, "Person ID is required"));
		String consortiumIdStr= Optional.ofNullable(input.get("consortiumId"))
			.map(Object::toString)
			.orElseThrow(() -> new HttpStatusException(HttpStatus.BAD_REQUEST, "Consortium ID is required"));
		UUID personId = UUID.fromString(idStr);
		UUID consortiumId =  UUID.fromString(consortiumIdStr);
		String reason = Optional.ofNullable(input.get("reason"))
			.map(Object::toString)
			.orElse("Removing consortium contact");
		String changeCategory = Optional.ofNullable(input.get("changeCategory"))
			.map(Object::toString)
			.orElse("Deletion");
		String changeReferenceUrl = Optional.ofNullable(input.get("changeReferenceUrl"))
			.map(Object::toString)
			.orElse("Removing consortium contact");

		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		Collection<String> roles = env.getGraphQlContext().get("roles");

		log.debug("deleteConsortiumContactDataFetcher: Attempting to delete consortium contact");

		// Check permissions - only ADMIN or LIBRARY_ADMIN can delete consortium contacts
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("deleteConsortiumContactDataFetcher: Access denied for user {}: user does not have the required role", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
		}


		return Mono.from(r2dbcOperations.withTransaction(status ->
			// First find the consortium contact to ensure it exists and to get the associated person
			Mono.from(consortiumContactRepository.findByConsortiumIdAndPersonId(consortiumId, personId))
				.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
					"Consortium contact for consortium "+consortiumId+ "with person ID " + personId + " not found")))
				.flatMap(consortiumContact -> {

					// Set audit information. We must update before deletion so it gets recorded in the data change log.
					consortiumContact.setReason(reason);
					consortiumContact.setLastEditedBy(userString);
					consortiumContact.setChangeCategory(changeCategory);
					consortiumContact.setChangeReferenceUrl(changeReferenceUrl);

					// If a consortium contact somehow doesn't have a person associated with it, it's worth making a note of.
					Person personToDelete = consortiumContact.getPerson();
					if (personToDelete == null) {
						log.debug("deleteConsortiumContactDataFetcher: Consortium contact {} has no associated person", personId);
						return Mono.error(new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
							"Consortium contact has no associated person record"));
					}

					// Update the entity with audit information before deletion
					return Mono.from(consortiumContactRepository.update(consortiumContact))
						.flatMap(updatedContact -> {
							// After updating with audit info, delete the consortium contact
							return Mono.from(consortiumContactRepository.delete(updatedContact.getId()))
								// After deleting the consortium contact, check if any other contacts reference this person
								.then(Mono.from(consortiumContactRepository.countByPersonId(personId)))
								.flatMap(count -> {
									if (count > 0) {
										// Other consortium contacts are using this person, don't delete the person record
										log.info("deleteConsortiumContactDataFetcher: Person {} is still referenced by {} other consortium contacts. Not deleting person record.",
											personId, count);
										return Mono.just(createResult(true,
											"Successfully deleted consortium contact. Person record was retained as it's still referenced by " + count + " other contacts."));
									} else {
										// No other references, safe to delete the person
										log.info("deleteConsortiumContactDataFetcher: No other references to Person {}. Proceeding with person deletion.", personToDelete.getId());
										return Mono.from(personRepository.delete(personToDelete.getId()))
											.then(Mono.just(createResult(true,
												"Successfully deleted consortium contact and associated person record")));
									}
								});
						})
						.onErrorResume(e -> {
							log.error("Error deleting consortium contact", e);
							return Mono.just(createResult(false, "Error deleting consortium contact: " + e.getMessage()));
						});
				})
		)).toFuture();
	}
	private Map<String, Object> createResult(boolean success, String message) {
		Map<String, Object> result = new HashMap<>();
		result.put("success", success);
		result.put("message", message);
		return result;
	}
}

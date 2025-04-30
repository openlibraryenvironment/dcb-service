package org.olf.dcb.graphql;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.extern.slf4j.Slf4j;

import org.olf.dcb.core.model.Person;
import org.olf.dcb.storage.LibraryContactRepository;
import org.olf.dcb.storage.PersonRepository;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Deletes a library contact and associated person object, if it's not linked to any other library contacts.
 */
@Singleton
@Slf4j
public class DeleteLibraryContactDataFetcher implements DataFetcher<CompletableFuture<Map<String, Object>>> {

	private final LibraryContactRepository libraryContactRepository;
	private final PersonRepository personRepository;
	private final R2dbcOperations r2dbcOperations;

	public DeleteLibraryContactDataFetcher(
		LibraryContactRepository libraryContactRepository,
		PersonRepository personRepository,
		R2dbcOperations r2dbcOperations) {

		this.libraryContactRepository = libraryContactRepository;
		this.personRepository = personRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<Map<String, Object>> get(DataFetchingEnvironment env) {
		Map<String, Object> input = env.getArgument("input");
		// This is the Person ID because this is what DCB Admin has to hand when the mutation is called.
		String idStr = Optional.ofNullable(input.get("personId"))
			.map(Object::toString)
			.orElseThrow(() -> new HttpStatusException(HttpStatus.BAD_REQUEST, "Person ID is required"));
		String libraryIdStr= Optional.ofNullable(input.get("libraryId"))
			.map(Object::toString)
			.orElseThrow(() -> new HttpStatusException(HttpStatus.BAD_REQUEST, "Library ID is required"));
		UUID personId = UUID.fromString(idStr);
		UUID libraryId =  UUID.fromString(libraryIdStr);
		String reason = Optional.ofNullable(input.get("reason"))
			.map(Object::toString)
			.orElse("Removing library contact");
		String changeCategory = Optional.ofNullable(input.get("changeCategory"))
			.map(Object::toString)
			.orElse("Removing library contact");
		String changeReferenceUrl = Optional.ofNullable(input.get("changeReferenceUrl"))
			.map(Object::toString)
			.orElse("Removing library contact");

		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		Collection<String> roles = env.getGraphQlContext().get("roles");

		log.debug("deleteLibraryContactDataFetcher: Attempting to delete library contact");

		// Check permissions - only ADMIN or LIBRARY_ADMIN can delete library contacts
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("deleteLibraryContactDataFetcher: Access denied for user {}: user does not have the required role", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
		}


		return Mono.from(r2dbcOperations.withTransaction(status ->
			// First find the library contact to ensure it exists and to get the associated person
			Mono.from(libraryContactRepository.findByLibraryIdAndPersonId(libraryId, personId))
				.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
					"Library contact for library "+libraryId+ "with person ID " + personId + " not found")))
				.flatMap(libraryContact -> {
					libraryContact.setReason(reason);
					libraryContact.setLastEditedBy(userString);
					libraryContact.setChangeCategory(changeCategory);
					libraryContact.setChangeReferenceUrl(changeReferenceUrl);
					Person personToDelete = libraryContact.getPerson();

					if (personToDelete == null) {
						log.debug("deleteLibraryContactDataFetcher: Library contact {} has no associated person", personId);
					}
					// First delete the library contact, then the person record IF no other library contacts are using it
					return Mono.from(libraryContactRepository.delete(libraryContact.getId()))
						// After deleting the library contact, check if any other contacts reference this person
						.then(Mono.from(libraryContactRepository.countByPersonId(personId)))
						.flatMap(count -> {
							if (count > 0) {
								// Other library contacts are using this person, don't delete the person record
								log.info("deleteLibraryContactDataFetcher: Person {} is still referenced by {} other library contacts. Not deleting person record.",
									personId, count);
								return Mono.just(createResult(true,
									"Successfully deleted library contact. Person record was retained as it's still referenced by "+count+" other contacts."));
							} else {
								// No other references, safe to delete the person
								log.info("deleteLibraryContactDataFetcher: No other references to Person {}. Proceeding with person deletion.", personToDelete.getId());
								return Mono.from(personRepository.delete(personToDelete.getId()))
									.then(Mono.just(createResult(true,
										"Successfully deleted library contact and associated person record")));
							}
						})
						.onErrorResume(e -> {
							log.error("Error deleting library contact", e);
							return Mono.just(createResult(false, "Error deleting library contact: " + e.getMessage()));
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

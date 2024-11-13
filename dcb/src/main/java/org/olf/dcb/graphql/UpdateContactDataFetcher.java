package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.Person;
import org.olf.dcb.storage.PersonRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Singleton
public class UpdateContactDataFetcher implements DataFetcher<CompletableFuture<Person>> {

	private final PersonRepository personRepository;

	private final R2dbcOperations r2dbcOperations;

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);


	public UpdateContactDataFetcher(PersonRepository personRepository, R2dbcOperations r2dbcOperations) {
		this.personRepository = personRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<Person> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("updateContactDataFetcher {}", input_map);

		UUID id = input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null;

		String role = input_map.get("role") != null ? input_map.get("role").toString() : null;
		String firstName = input_map.get("firstName") != null ? input_map.get("firstName").toString() : null;
		String lastName = input_map.get("lastName") != null ? input_map.get("lastName").toString() : null;
		String email = input_map.get("email") != null ? input_map.get("email").toString() : null;
		Boolean isPrimaryContact = input_map.containsKey("isPrimaryContact") ?
			Boolean.parseBoolean(input_map.get("isPrimaryContact").toString()) : null;

		Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString);
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString);
		String userString = Optional.ofNullable(env.getGraphQlContext().get("currentUser"))
			.map(Object::toString)
			.orElse("User not detected");

		Collection<String> roles = env.getGraphQlContext().get("roles");

		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("updateContactDataFetcher: Access denied for user {}: user does not have the required role to update a library contact.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}


		Mono<Person> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(personRepository.findById(id))
				.flatMap(person -> {
					if (role != null) {
						person.setRole(role);
					}
					if (firstName != null) {
						person.setFirstName(firstName);
					}
					if (lastName != null) {
						person.setLastName(lastName);
					}
					if (email != null) {
						person.setEmail(email);
					}
					if (isPrimaryContact != null) {
						person.setIsPrimaryContact(isPrimaryContact);
					}
					person.setLastEditedBy(userString);
					changeReferenceUrl.ifPresent(person::setChangeReferenceUrl);
					changeCategory.ifPresent(person::setChangeCategory);
					reason.ifPresent(person::setReason);
					return Mono.from(personRepository.update(person));
				})
		));

		return transactionMono.toFuture();
	}
}

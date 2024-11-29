package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.Person;
import org.olf.dcb.core.model.RoleName;
import org.olf.dcb.storage.PersonRepository;

import org.olf.dcb.storage.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Singleton
@Slf4j
public class UpdateContactDataFetcher implements DataFetcher<CompletableFuture<Person>> {

	private final PersonRepository personRepository;
	private final RoleRepository roleRepository;
	private final R2dbcOperations r2dbcOperations;

	public UpdateContactDataFetcher(PersonRepository personRepository, RoleRepository roleRepository, R2dbcOperations r2dbcOperations) {
		this.personRepository = personRepository;
		this.roleRepository = roleRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<Person> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("updateContactDataFetcher {}", input_map);

		UUID id = input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null;

		String roleString = input_map.get("role") != null ? input_map.get("role").toString() : null;
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
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		Collection<String> roles = env.getGraphQlContext().get("roles");

		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("updateContactDataFetcher: Access denied for user {}: user does not have the required role to update a library contact.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}


		Mono<Person> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(personRepository.findById(id))
				.flatMap(person -> {
					if (roleString != null) {
						// If role edit requested, we must first validate the role name
						if (!RoleName.isValid(roleString)) {
							return Mono.error(new IllegalArgumentException(
								String.format("Invalid role: '%s'. The roles currently available are: %s",
									roleString,
									RoleName.getValidNames())
							));
						}

						// We then convert the role name string to the RoleName enum and use it to look up the role to be set
						return Mono.from(roleRepository.findByName(RoleName.valueOf(roleString)))
							.flatMap(role -> {
								person.setRole(role);
								// And then we proceed with any further edits
								// As we have established a different reactive flow
								if (firstName != null) person.setFirstName(firstName);
								if (lastName != null) person.setLastName(lastName);
								if (email != null) person.setEmail(email);
								if (isPrimaryContact != null) person.setIsPrimaryContact(isPrimaryContact);
								person.setLastEditedBy(userString);
								changeReferenceUrl.ifPresent(person::setChangeReferenceUrl);
								changeCategory.ifPresent(person::setChangeCategory);
								reason.ifPresent(person::setReason);
								return Mono.from(personRepository.update(person));
							});
					}
					// If the role is null, process other edits as normal.
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

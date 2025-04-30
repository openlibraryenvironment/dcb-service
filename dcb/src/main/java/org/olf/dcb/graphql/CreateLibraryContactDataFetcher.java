package org.olf.dcb.graphql;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.Arrays;

import java.util.concurrent.CompletableFuture;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.Person;
import org.olf.dcb.core.model.LibraryContact;
import org.olf.dcb.core.model.RoleName;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.LibraryContactRepository;
import org.olf.dcb.storage.PersonRepository;
import org.olf.dcb.storage.RoleRepository;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

/**
 * Creates a contact for a library.
 */
@Singleton
@Slf4j
public class CreateLibraryContactDataFetcher implements DataFetcher<CompletableFuture<LibraryContact>> {

	private PersonRepository personRepository;
	private LibraryContactRepository libraryContactRepository;
	private LibraryRepository libraryRepository;
	private RoleRepository roleRepository;
	private R2dbcOperations r2dbcOperations;

	public CreateLibraryContactDataFetcher(PersonRepository personRepository, LibraryContactRepository libraryContactRepository,
																				 LibraryRepository libraryRepository, RoleRepository roleRepository,
																				 R2dbcOperations r2dbcOperations) {
		this.libraryContactRepository = libraryContactRepository;
		this.libraryRepository = libraryRepository;
		this.personRepository = personRepository;
		this.roleRepository = roleRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<LibraryContact> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		String reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString)
			.orElse("Adding a new library contact");
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");
		UUID libraryId = input_map.get("libraryId") != null ? UUID.fromString(input_map.get("libraryId").toString()) : null;
		Collection<String> roles = env.getGraphQlContext().get("roles");
		log.debug("createLibraryContactDataFetcher input: {}, libraryId: {}", input_map, libraryId);
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("createLibraryContactDataFetcher: Access denied for user {}: user does not have the required role to update a library contact.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
		}

		String roleString = input_map.get("role").toString().trim().replace("-", "_");

		// Role name enum validation
		String newRoleString = roleString.replace(" ", "_").toUpperCase();
		if (!RoleName.isValid(newRoleString)) {
			throw new IllegalArgumentException(
				String.format("Invalid role: '%s'. Valid contact roles are: %s",
					roleString,
					RoleName.getValidNames())
			);
		}
		RoleName roleName = RoleName.valueOf(newRoleString);

		return Mono.from(r2dbcOperations.withTransaction(status ->
			// First, find the role
			Mono.from(roleRepository.findByName(roleName))
				.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
					String.format("Role '%s' not found in the list of valid roles. Valid roles are '%s'.", roleName, RoleName.getValidNames()))))
				.flatMap(role -> {
					// If we find that the role is valid, we can create a person
					Person newPerson = Person.builder()
						.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
							"Person:" + input_map.get("firstName") + input_map.get("lastName") +
								input_map.get("role") + input_map.get("email")))
						.firstName(input_map.get("firstName").toString())
						.lastName(input_map.get("lastName").toString())
						.role(role)
						.email(input_map.get("email").toString())
						.isPrimaryContact(Boolean.parseBoolean(input_map.get("isPrimaryContact").toString()))
						.changeCategory("New contact")
						.reason(reason)
						.lastEditedBy(userString)
						.build();

					changeReferenceUrl.ifPresent(newPerson::setChangeReferenceUrl);

					// Save person and create library contact
					return Mono.from(personRepository.save(newPerson))
						.flatMap(savedPerson ->
							Mono.from(libraryRepository.findById(libraryId))
								.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
									"Library with libraryId " + libraryId + " not found")))
								.flatMap(library -> {
									LibraryContact contact = LibraryContact.builder()
										.library(library)
										.person(savedPerson)
										.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
											"LibraryContact:" + library.getId() + "," + savedPerson.getId()))
										.build();

									return Mono.from(libraryContactRepository.saveOrUpdate(contact));
								})
						);
				})
		)).toFuture();
	}
}

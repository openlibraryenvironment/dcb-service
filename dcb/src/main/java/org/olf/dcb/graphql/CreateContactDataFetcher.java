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
import org.olf.dcb.core.model.ConsortiumContact;
import org.olf.dcb.core.model.RoleName;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.ConsortiumContactRepository;
import org.olf.dcb.storage.PersonRepository;
import org.olf.dcb.storage.RoleRepository;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

// Creates a contact for a consortium. Could be extended further to also support creating a library contact.
@Singleton
@Slf4j
public class CreateContactDataFetcher implements DataFetcher<CompletableFuture<ConsortiumContact>> {

	private PersonRepository personRepository;

	private ConsortiumContactRepository consortiumContactRepository;

	private ConsortiumRepository consortiumRepository;

	private RoleRepository roleRepository;


	private R2dbcOperations r2dbcOperations;

	public CreateContactDataFetcher(PersonRepository personRepository, ConsortiumContactRepository consortiumContactRepository,
																	ConsortiumRepository consortiumRepository, RoleRepository roleRepository,
																	R2dbcOperations r2dbcOperations) {
			this.consortiumContactRepository = consortiumContactRepository;
			this.consortiumRepository = consortiumRepository;
			this.personRepository = personRepository;
			this.roleRepository = roleRepository;
			this.r2dbcOperations = r2dbcOperations;
		}

		@Override
		public CompletableFuture<ConsortiumContact> get(DataFetchingEnvironment env) {
			Map<String, Object> input_map = env.getArgument("input");
			String reason = Optional.ofNullable(input_map.get("reason"))
				.map(Object::toString)
				.orElse("Adding a new contact");
			Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
				.map(Object::toString);
			String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
				.map(Object::toString)
				.orElse("User not detected");
			UUID consortiumId = input_map.get("consortiumId") != null ? UUID.fromString(input_map.get("consortiumId").toString()) : null;
			Collection<String> roles = env.getGraphQlContext().get("roles");
			log.debug("createContactDataFetcher input: {}, consortiumId: {}", input_map, consortiumId);
			if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
				log.warn("createContactDataFetcher: Access denied for user {}: user does not have the required role to update a consortium contact.", userString);
				throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
			}

			String roleString = input_map.get("role").toString().trim().replace("-", "_");
			String[] validRoles = new String[]{RoleName.getValidNames()};


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
						String.format("Role '%s' not found in the list of valid roles. Valid roles are '%s'.", roleName, Arrays.toString(validRoles)))))
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

						// Save person and create consortium contact
						return Mono.from(personRepository.save(newPerson))
							.flatMap(savedPerson ->
								Mono.from(consortiumRepository.findById(consortiumId))
									.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
										"Consortium with consortiumId " + consortiumId + " not found")))
									.flatMap(consortium -> {
										ConsortiumContact contact = ConsortiumContact.builder()
											.consortium(consortium)
											.person(savedPerson)
											.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
												"ConsortiumContact:" + consortium.getId() + "," + savedPerson.getId()))
											.lastEditedBy(userString)
											.changeCategory("New contact")
											.reason("Association created between new contact and consortium")
											.build();

										return Mono.from(consortiumContactRepository.saveOrUpdate(contact));
									})
							);
					})
			)).toFuture();
		}
}



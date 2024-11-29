package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.Role;
import org.olf.dcb.core.model.RoleName;
import org.olf.dcb.storage.RoleRepository;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

@Singleton
@Slf4j
public class CreateRoleDataFetcher implements DataFetcher<CompletableFuture<Role>> {

	private RoleRepository roleRepository;
	private R2dbcOperations r2dbcOperations;

	public CreateRoleDataFetcher(RoleRepository roleRepository, R2dbcOperations r2dbcOperations) {
		this.roleRepository = roleRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	// This also needs to associate a role with a person + fail if it can't.
	@Override
	public CompletableFuture<Role> get(DataFetchingEnvironment env) {

		Map<String, Object> input_map = env.getArgument("input");
		log.debug("createRoleDataFetcher {}", input_map);

		Collection<String> roles = env.getGraphQlContext().get("roles");
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		if (roles == null || (!roles.contains("ADMIN"))) {
			log.warn("createRoleDataFetcher: Access denied for user {}: user does not have the required role to create a contact role.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to create a contact role.");
		}

		String reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString)
			.orElse("Creation of contact role");

		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		String changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString)
			.orElse("Add role");

		String roleString = input_map.get("name").toString().trim();
		// First validate the role name. We have a RoleName enum for ensuring that only valid roles can be added.
		if (!RoleName.isValid(roleString)) {
			throw new IllegalArgumentException(
				String.format("Invalid role: '%s'. The roles currently available are: %s",
					roleString,
					RoleName.getValidNames())
			);
		}
		// If valid, we can begin adding the role
		RoleName roleName = RoleName.valueOf(roleString);

		Role newRole = Role.builder()
			.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Role:" + roleString))
			.name(roleName)
			.displayName(input_map.get("displayName").toString())
			.keycloakRole(input_map.get("keycloakRole").toString())
			.description(input_map.get("description").toString())
			.lastEditedBy(userString)
			.reason(reason)
			.changeCategory(changeCategory).build();
		changeReferenceUrl.ifPresent(newRole::setChangeReferenceUrl);
		return Mono.from(r2dbcOperations.withTransaction(status -> Mono.from(roleRepository.saveOrUpdate(newRole)))).toFuture();
	}

}

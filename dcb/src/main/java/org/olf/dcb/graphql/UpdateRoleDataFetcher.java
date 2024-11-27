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
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
@Slf4j
public class UpdateRoleDataFetcher implements DataFetcher<CompletableFuture<Role>> {

	private final RoleRepository roleRepository;
	private final R2dbcOperations r2dbcOperations;

	public UpdateRoleDataFetcher(RoleRepository roleRepository, R2dbcOperations r2dbcOperations) {
		this.roleRepository = roleRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<Role> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("updateRoleDataFetcher {}", input_map);
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");
		Collection<String> roles = env.getGraphQlContext().get("roles");

		// Check if the user has the required role to edit role information
		if (roles == null || (!roles.contains("ADMIN"))) {
			log.warn("updateRoleDataFetcher: Access denied for user {}: user does not have the required role to update a role.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}


		UUID id = input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null;


		String roleString = input_map.get("name").toString().trim();
		// First validate the role name. We have a RoleName enum for ensuring that only valid roles can be added.
		// The role name must be provided in order for us to look it up.
		if (!RoleName.isValid(roleString)) {
			throw new IllegalArgumentException(
				String.format("Invalid role: '%s'. The roles currently available are: %s",
					roleString,
					RoleName.getValidNames())
			);
		}
		RoleName roleName = RoleName.valueOf(roleString);
		Optional<String> displayName = Optional.ofNullable(input_map.get("displayName"))
			.map(Object::toString);
		Optional<String> keycloakRole = Optional.ofNullable(input_map.get("keycloakRole"))
			.map(Object::toString);
		Optional<String> description = Optional.ofNullable(input_map.get("name"))
			.map(Object::toString);

		Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString);
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString);



		Mono<Role> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(roleRepository.findByName(roleName))
				.flatMap(role -> {
					displayName.ifPresent(role::setDisplayName);
					keycloakRole.ifPresent(role::setKeycloakRole);
					description.ifPresent(role::setDescription);
					role.setLastEditedBy(userString);
					changeReferenceUrl.ifPresent(role::setChangeReferenceUrl);
					changeCategory.ifPresent(role::setChangeCategory);
					reason.ifPresent(role::setReason);
					return Mono.from(roleRepository.update(role));
				})
		));

		return transactionMono.toFuture();
	}
}

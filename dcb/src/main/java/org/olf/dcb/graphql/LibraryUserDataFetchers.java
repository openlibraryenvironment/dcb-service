package org.olf.dcb.graphql;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.model.LibraryUserAccount;
import org.olf.dcb.security.provisioning.LibraryUserProvisioningService;
import org.olf.dcb.security.provisioning.ProvisionableRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;

/**
 * The account provisioning surface.
 *
 * <h2>Consortium roles only, and LIBRARY_ADMIN deliberately absent</h2>
 *
 * A library administrator provisioning their own colleagues is a defensible future feature.
 * It is a different threat model - self-service escalation within a library - and it is not
 * this one. {@link GraphQLRoles#CONSORTIUM} is the whole allow-list.
 *
 * <h2>Why the role is re-parsed after the schema already validated it</h2>
 *
 * A resolver that trusts its schema is one schema edit away from being wrong, and the value
 * being trusted is the one that decides what authority gets minted. See
 * {@link ProvisionableRole} for the other two gates.
 */
@Singleton
public class LibraryUserDataFetchers {

	private static final Logger log = LoggerFactory.getLogger(LibraryUserDataFetchers.class);

	private final LibraryUserProvisioningService provisioningService;

	public LibraryUserDataFetchers(LibraryUserProvisioningService provisioningService) {
		this.provisioningService = provisioningService;
	}

	public DataFetcher<CompletableFuture<List<LibraryUserAccount>>> getLibraryUsersDataFetcher() {
		return env -> {
			GraphQLRoles.require(env, "libraryUsers", GraphQLRoles.CONSORTIUM);

			final var libraryId = requiredUuid(env.getArgument("libraryId"), "libraryId");

			return provisioningService.list(libraryId).collectList().toFuture();
		};
	}

	/**
	 * Whether provisioning is configured at all.
	 *
	 * <p>Gated like everything else here rather than left open: it discloses a fact about
	 * how the deployment is wired, and only consortium staff have any use for it.
	 */
	public DataFetcher<CompletableFuture<Boolean>> getProvisioningAvailableDataFetcher() {
		return env -> {
			GraphQLRoles.require(env, "libraryUserProvisioningAvailable", GraphQLRoles.CONSORTIUM);

			return CompletableFuture.completedFuture(provisioningService.isConfigured());
		};
	}

	public DataFetcher<CompletableFuture<LibraryUserAccount>> provisionLibraryUserDataFetcher() {
		return env -> {
			GraphQLRoles.require(env, "provisionLibraryUser", GraphQLRoles.CONSORTIUM);

			final var input = input(env);
			final var role = ProvisionableRole.parse(input.get("role"))
				.orElseThrow(() -> new HttpStatusException(HttpStatus.BAD_REQUEST,
					"Invalid role. Provisionable roles are: " + ProvisionableRole.valid()));

			final var email = requiredString(input.get("email"), "email");

			log.info("Provisioning a {} account for library {} at the request of {}",
				role, input.get("libraryId"), actor(env));

			return provisioningService.provision(
					requiredUuid(input.get("libraryId"), "libraryId"),
					email,
					optionalString(input.get("firstName")),
					optionalString(input.get("lastName")),
					role,
					actor(env),
					optionalString(input.get("reason")),
					optionalString(input.get("changeCategory")),
					optionalString(input.get("changeReferenceUrl")))
				.toFuture();
		};
	}

	public DataFetcher<CompletableFuture<LibraryUserAccount>> setLibraryUserEnabledDataFetcher() {
		return env -> {
			GraphQLRoles.require(env, "setLibraryUserEnabled", GraphQLRoles.CONSORTIUM);

			final var input = input(env);

			return provisioningService.setEnabled(
					requiredUuid(input.get("id"), "id"),
					Boolean.TRUE.equals(input.get("enabled")),
					actor(env),
					optionalString(input.get("reason")),
					optionalString(input.get("changeCategory")),
					optionalString(input.get("changeReferenceUrl")))
				.toFuture();
		};
	}

	public DataFetcher<CompletableFuture<LibraryUserAccount>> resendLibraryUserInviteDataFetcher() {
		return env -> {
			GraphQLRoles.require(env, "resendLibraryUserInvite", GraphQLRoles.CONSORTIUM);

			return provisioningService
				.resendInvite(requiredUuid(input(env).get("id"), "id"))
				.toFuture();
		};
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> input(DataFetchingEnvironment env) {
		return Optional.<Map<String, Object>>ofNullable(env.getArgument("input"))
			.orElseThrow(() -> new HttpStatusException(HttpStatus.BAD_REQUEST,
				"An input is required."));
	}

	private static String actor(DataFetchingEnvironment env) {
		return Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");
	}

	private static UUID requiredUuid(Object value, String field) {
		try {
			return UUID.fromString(requiredString(value, field));
		}
		catch (IllegalArgumentException notAUuid) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, field + " is not a valid id.");
		}
	}

	private static String requiredString(Object value, String field) {
		final var text = optionalString(value);

		if (text == null || text.isBlank()) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
		}

		return text;
	}

	private static String optionalString(Object value) {
		return value == null ? null : value.toString().trim();
	}
}

package org.olf.dcb.graphql;

import graphql.GraphQLContext;
import graphql.ExecutionInput;
import io.micronaut.configuration.graphql.GraphQLExecutionInputCustomizer;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.olf.dcb.security.AgencyClaims;
import reactor.core.publisher.Mono;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
@Primary
public class GraphQLSecurityContextCustomizer implements GraphQLExecutionInputCustomizer {
	private final SecurityService securityService;

	private static Logger log = LoggerFactory.getLogger(GraphQLSecurityContextCustomizer.class);

	// This is inspired by the official micronaut-graphql example here https://github.com/micronaut-projects/micronaut-graphql/blob/4.4.x/examples/jwt-security/src/main/java/example/graphql/RequestResponseCustomizer.java
	// The idea is that we inject the security service here (where the security context is available), get the username and put it in GraphQL context,
	// and then can access it from data fetchers where we previously could not.

	public GraphQLSecurityContextCustomizer(
		SecurityService securityService) {
		this.securityService = securityService;
	}

	@Override
	public Publisher<ExecutionInput> customize(ExecutionInput executionInput,
																						 HttpRequest httpRequest,
																						 @Nullable MutableHttpResponse<String> httpResponse) {

		// Uncomment this if this method requires debugging. It will tell you if the username is being fetched properly.
		// log.debug("Username from SCC: {}", securityService.username().toString());

		// This method gets the current user's information, if present, and saves it into the GraphQl context
		// Thus giving us access to user information when performing GraphQL operations (i.e. for data change log purposes).

		return Mono.fromCallable(() -> {
			GraphQLContext context = executionInput.getGraphQLContext();
			securityService.getAuthentication().ifPresent(auth -> {
				String prefName = auth.getName();
				Map<String, Object> attributes = auth.getAttributes();
				String userID = stringAttribute(attributes, "sub").orElse(prefName);
				String email = stringAttribute(attributes, "email").orElse(null);
				String name = stringAttribute(attributes, "name")
					.or(() -> stringAttribute(attributes, "preferred_username"))
					.orElse(prefName);
				Collection<String> roles = rolesFrom(auth.getRoles(), attributes);
				Collection<String> agencyCodes = agencyCodesFrom(attributes);

				putIfPresent(context, "currentUser", userID);
				putIfPresent(context, "userName", prefName);
				putIfPresent(context, "userEmail", email);
				putIfPresent(context, "userFullName", name);
				context.put("roles", roles);
				context.put(AGENCY_CODES, agencyCodes);
			});
			return executionInput;
		});
	}

	private static Optional<String> stringAttribute(Map<String, Object> attributes, String name) {
		return Optional.ofNullable(attributes.get(name))
			.filter(String.class::isInstance)
			.map(String.class::cast);
	}

	private static void putIfPresent(GraphQLContext context, String key, Object value) {
		if (value != null) {
			context.put(key, value);
		}
	}

	/** Context key for the agencies a request may see data for. */
	public static final String AGENCY_CODES = "agencyCodes";

	/**
	 * The agencies this user is responsible for.
	 * <p>
	 * Delegates to {@link AgencyClaims}, which the statistics endpoints read through
	 * as well - the same token must not scope one way here and another way there.
	 * That class documents why the claim is {@code code}, why it is read as a
	 * collection, and why empty is not "no restriction" (see AgencyAccessScope,
	 * where it means the opposite).
	 */
	static Collection<String> agencyCodesFrom(Map<String, Object> attributes) {
		return AgencyClaims.from(attributes);
	}

	static Collection<String> rolesFrom(Collection<String> authenticationRoles, Map<String, Object> attributes) {
		Set<String> roles = new LinkedHashSet<>();
		addValues(roles, authenticationRoles);
		addValues(roles, attributes.get("roles"));
		addRolesFromMap(roles, attributes.get("realm_access"));
		addResourceAccessRoles(roles, attributes.get("resource_access"));
		addZitadelProjectRoles(roles, attributes);
		return new ArrayList<>(roles);
	}

	/** Tolerates a claim issued either as a single string or as a list of them. */
	private static void addValues(Set<String> roles, Object value) {
		if (value instanceof Collection<?> collection) {
			collection.stream()
				.filter(String.class::isInstance)
				.map(String.class::cast)
				.forEach(roles::add);
		}
		else if (value instanceof String role) {
			roles.add(role);
		}
	}

	@SuppressWarnings("unchecked")
	private static void addRolesFromMap(Set<String> roles, Object value) {
		if (value instanceof Map<?, ?> map) {
			addValues(roles,((Map<String, Object>) map).get("roles"));
		}
	}

	private static void addResourceAccessRoles(Set<String> roles, Object value) {
		if (value instanceof Map<?, ?> resourceAccess) {
			resourceAccess.values().forEach(resource -> addRolesFromMap(roles, resource));
		}
	}

	private static void addZitadelProjectRoles(Set<String> roles, Map<String, Object> attributes) {
		attributes.entrySet().stream()
			.filter(entry -> entry.getKey().startsWith("urn:zitadel:iam:org:project"))
			.filter(entry -> entry.getKey().endsWith(":roles") || entry.getKey().equals("urn:zitadel:iam:org:project:roles"))
			.forEach(entry -> addZitadelRoleClaim(roles, entry.getValue()));
	}

	private static void addZitadelRoleClaim(Set<String> roles, Object value) {
		if (value instanceof Map<?, ?> map) {
			map.keySet().stream()
				.filter(String.class::isInstance)
				.map(String.class::cast)
				.forEach(roles::add);
		}
		else {
			addValues(roles,value);
		}
	}
}

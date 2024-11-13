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
import reactor.core.publisher.Mono;
import java.util.Collection;

@Singleton
@Primary
public class GraphQLSecurityContextCustomizer implements GraphQLExecutionInputCustomizer {
	private final SecurityService securityService;

	private static Logger log = LoggerFactory.getLogger(GraphQLSecurityContextCustomizer.class);

	// This is inspired by the official micronaut-graphql example here https://github.com/micronaut-projects/micronaut-graphql/blob/4.4.x/examples/jwt-security/src/main/java/example/graphql/RequestResponseCustomizer.java
	// The idea is that we inject the security service here (where the security context is available), get the username and put it in GraphQL context,
	// and then can access it from data fetchers where we previously could not.

	public GraphQLSecurityContextCustomizer(SecurityService securityService) {
		this.securityService = securityService;
	}

	@Override
	public Publisher<ExecutionInput> customize(ExecutionInput executionInput,
																						 HttpRequest httpRequest,
																						 @Nullable MutableHttpResponse<String> httpResponse) {

		// Uncomment this if this method requires debugging. It will tell you if the username is being fetched properly.
//		log.debug("Username from SCC: {}", securityService.username().toString());

		// Gets the username if authentication is present, and then puts it into the GraphQL context.
		//

		return Mono.fromCallable(() -> {
			GraphQLContext context = executionInput.getGraphQLContext();
			securityService.getAuthentication().ifPresent(auth -> {
				// Get the user info
				String userID = auth.getName();
				String prefName = (String) auth.getAttributes().get("preferred_username");
				String email = (String) auth.getAttributes().get("email");
				String name = (String) auth.getAttributes().get("name");

				log.debug(prefName);
				log.debug("Email {}, name {}", email, name);
				// Get the roles (assuming roles are stored in the "roles" attribute)
				// We are suppressing this warning because we know the roles we're getting from the security service will be in an acceptable format.
				@SuppressWarnings("unchecked")
				Collection<String> roles = (Collection<String>) auth.getAttributes().get("roles");

				// Log the userID and roles
				log.debug("Roles: {}, Username: {}", roles, userID);

				// Store them in the GraphQL context
				context.put("currentUser", userID);
				context.put("userName", prefName);
				context.put("userEmail", email);
				context.put("userFullName", name);
				context.put("roles", roles);
			});
			return executionInput;
		});
	}
}

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
		// This means we can then access with context.get("currentUser") in our data fetchers.
		return Mono.fromCallable(() -> {
			GraphQLContext context = executionInput.getGraphQLContext();
			securityService.getAuthentication().ifPresent(auth ->
				context.put("currentUser", auth.getName())
			);
			return executionInput;
		});
	}
}

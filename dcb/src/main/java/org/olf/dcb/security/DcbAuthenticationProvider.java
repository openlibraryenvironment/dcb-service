package org.olf.dcb.security;

import java.util.List;

import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@io.micronaut.context.annotation.Requires(env = {io.micronaut.context.env.Environment.DEVELOPMENT, io.micronaut.context.env.Environment.TEST, "development"})
@Singleton
public class DcbAuthenticationProvider implements AuthenticationProvider<HttpRequest<?>> {

	private static List VALID_USERS = List.of( "user", "admin", "standard" );

	private final Logger log = LoggerFactory.getLogger(DcbAuthenticationProvider.class);

	@Override
	public Publisher<AuthenticationResponse> authenticate(@Nullable HttpRequest<?> httpRequest, AuthenticationRequest<?, ?> authenticationRequest) {

		log.debug("DcbAuthenticationProvider::authenticate {}",authenticationRequest.getIdentity());
		return Flux.create(emitter -> {
			if (VALID_USERS.contains(authenticationRequest.getIdentity())) {
				// If the username is admin, allocate the admin role
                                List<String> roles = authenticationRequest.getIdentity().equals("admin") ? List.of("ADMIN") : List.of( "STD" );
				emitter.next(AuthenticationResponse.success((String) authenticationRequest.getIdentity(),  roles));
				emitter.complete();
			} else {
				log.info("Invalid user in DcbAuthenticationProvider");
				emitter.error(AuthenticationResponse.exception());
			}
		}, FluxSink.OverflowStrategy.ERROR);
	}
}

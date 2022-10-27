package org.olf.reshare.dcb;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Singleton
public class DcbAuthenticationProvider implements AuthenticationProvider {
	
  // TODO: Change
  private static String USERNAME = "user";
  private static String PASSWORD = "password";
	
  @Override
    public Publisher<AuthenticationResponse> authenticate(HttpRequest<?> httpRequest, AuthenticationRequest<?, ?> authenticationRequest) {
        
    return Mono.<AuthenticationResponse>create(emitter -> {
      if (authenticationRequest.getIdentity().equals(USERNAME) && authenticationRequest.getSecret().equals(PASSWORD)) {
        emitter.success(AuthenticationResponse.success(USERNAME));
      } else {
        emitter.error(AuthenticationResponse.exception());
      } 
    });
  }
}
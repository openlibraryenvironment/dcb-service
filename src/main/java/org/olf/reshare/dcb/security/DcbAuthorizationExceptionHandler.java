package org.olf.reshare.dcb.security;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.authentication.AuthorizationException;
import io.micronaut.security.authentication.DefaultAuthorizationExceptionHandler;
import jakarta.inject.Singleton;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.UNAUTHORIZED;
//import static io.micronaut.http.HttpHeaders.WWW_AUTHENTICATE;

@Singleton
@Replaces(DefaultAuthorizationExceptionHandler.class)
public class DcbAuthorizationExceptionHandler extends DefaultAuthorizationExceptionHandler {
	
  @Override
  protected MutableHttpResponse<?> httpResponseWithStatus(HttpRequest<?> request, AuthorizationException e){
    // 401
    if (request.getHeaders().contains("Authorization")) {
      System.out.println("Unauthorized for: " + request);
      return HttpResponse
    		  .status(UNAUTHORIZED);
    }
    // 400
    return HttpResponse
      .unauthorized()
      .status(BAD_REQUEST);
  }
}

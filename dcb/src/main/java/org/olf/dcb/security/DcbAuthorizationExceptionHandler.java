package org.olf.dcb.security;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.UNAUTHORIZED;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.security.authentication.AuthorizationException;
import io.micronaut.security.authentication.DefaultAuthorizationExceptionHandler;
import io.micronaut.security.config.RedirectConfiguration;
import io.micronaut.security.config.RedirectService;
import io.micronaut.security.errors.PriorToLoginPersistence;
import jakarta.inject.Singleton;

@Singleton
@Replaces(DefaultAuthorizationExceptionHandler.class)
public class DcbAuthorizationExceptionHandler extends DefaultAuthorizationExceptionHandler {

        public DcbAuthorizationExceptionHandler( 
            ErrorResponseProcessor<?> errorResponseProcessor, 
            RedirectConfiguration redirectConfiguration, 
            RedirectService redirectService, 
            @Nullable PriorToLoginPersistence<?,?> priorToLoginPersistence) {
            super(errorResponseProcessor,redirectConfiguration,redirectService,priorToLoginPersistence);
        }

	@Override
	protected MutableHttpResponse<?> httpResponseWithStatus(HttpRequest<?> request, AuthorizationException e) {
		// 401
		if (request.getHeaders().contains("Authorization")) {
			System.out.println("Unauthorized for: " + request);
			return HttpResponse.status(UNAUTHORIZED);
		}
		// 400
		return HttpResponse.unauthorized().status(BAD_REQUEST);
	}
}

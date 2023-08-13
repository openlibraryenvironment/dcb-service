package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.TEXT_PLAIN;

import org.olf.dcb.core.HostLmsService.UnknownHostLmsException;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Produces
@Singleton
@Requires(classes = {UnknownHostLmsException.class, ExceptionHandler.class})
public class UnknownHostLmsExceptionHandler
	implements ExceptionHandler<UnknownHostLmsException, HttpResponse> {

	@Override
	public HttpResponse handle(HttpRequest request, UnknownHostLmsException exception) {
		return HttpResponse.badRequest()
			.contentType(TEXT_PLAIN)
			.body(exception.getMessage());
	}
}

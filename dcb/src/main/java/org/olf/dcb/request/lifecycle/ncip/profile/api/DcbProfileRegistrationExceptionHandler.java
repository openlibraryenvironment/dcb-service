package org.olf.dcb.request.lifecycle.ncip.profile.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import java.util.UUID;
import org.olf.dcb.request.lifecycle.ncip.profile.application.DcbProfileRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DcbProfileRegistrationExceptionHandler
	implements ExceptionHandler<DcbProfileRegistrationException, HttpResponse<DcbProfileRegistrationApi.Problem>> {

	private static final Logger log = LoggerFactory.getLogger(DcbProfileRegistrationExceptionHandler.class);
	private static final MediaType PROBLEM_JSON = MediaType.of("application/problem+json");

	@Override
	public HttpResponse<DcbProfileRegistrationApi.Problem> handle(
		HttpRequest request,
		DcbProfileRegistrationException exception
	) {
		String correlationId = UUID.randomUUID().toString();
		log.warn(
			"DCB profile registration rejected code={} path={} correlationId={} detail={}",
			exception.code(),
			request.getPath(),
			correlationId,
			exception.getMessage()
		);
		return HttpResponse.status(exception.status())
			.contentType(PROBLEM_JSON)
			.body(new DcbProfileRegistrationApi.Problem(
				"urn:openrs:dcb-profile-registration:" + exception.code().toLowerCase(),
				"DCB Profile NCIP2.02+ registration failed",
				exception.status().getCode(),
				exception.getMessage(),
				request.getPath(),
				exception.code(),
				exception.field(),
				exception.prerequisite(),
				exception.retryable(),
				correlationId
			));
	}
}

package org.olf.dcb.core.interaction;

import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static io.micronaut.http.HttpStatus.UNAUTHORIZED;

import java.util.function.Predicate;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class HttpResponsePredicates {
	public static boolean isUnauthorised(Throwable throwable) {
		return isClientResponseException(throwable, isUnauthorised());
	}

	public static boolean isUnprocessableContent(Throwable throwable) {
		return isClientResponseException(throwable, isUnprocessableContent());
	}

	public static Predicate<HttpResponse<?>> isUnauthorised() {
		return isStatus(UNAUTHORIZED);
	}

	public static Predicate<HttpResponse<?>> isNotFound() {
		return isStatus(NOT_FOUND);
	}

	public static Predicate<HttpResponse<?>> isUnprocessableContent() {
		return isStatus(HttpStatus.valueOf(422));
	}

	public static Predicate<HttpResponse<?>> isStatus(HttpStatus status) {
		return r -> r.getStatus() == status;
	}

	public static boolean isClientResponseException(Throwable throwable,
		Predicate<HttpResponse<?>> responsePredicate) {

		if (throwable instanceof HttpClientResponseException exception) {
			return responsePredicate.test(exception.getResponse());
		}
		else {
			return false;
		}
	}
}

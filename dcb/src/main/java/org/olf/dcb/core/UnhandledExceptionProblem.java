package org.olf.dcb.core;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.zalando.problem.Status.INTERNAL_SERVER_ERROR;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.Map;

import org.zalando.problem.AbstractThrowableProblem;

public class UnhandledExceptionProblem extends AbstractThrowableProblem {
	public UnhandledExceptionProblem(Throwable unhandledException) {
		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			"Unhandled exception: \"%s\"".formatted(
				getValue(unhandledException, Throwable::getMessage, "Unknown Message")),
			INTERNAL_SERVER_ERROR, getValue(unhandledException, Object::toString, ""), null, null,
			Map.of("stackTrace", stackTraceToString(unhandledException)));
	}

	private static String stackTraceToString(Throwable throwable) {
		if (throwable == null) {
			return "Unknown stack trace";
		}

		final var sw = new StringWriter();
		final var pw = new PrintWriter(sw);
		throwable.printStackTrace(pw);

		return sw.toString();
	}
}

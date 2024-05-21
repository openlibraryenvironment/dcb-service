package org.olf.dcb.request.workflow.exceptions;

import java.net.URI;
import java.util.Map;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.StatusType;

import reactor.core.publisher.Mono;
import services.k_int.utils.ReactorUtils;

public class UnableToResolveAgencyProblem extends AbstractThrowableProblem {
	public static <T> Mono<T> raiseError(String homeLibraryCode, String systemCode) {
		return ReactorUtils.raiseError(new UnableToResolveAgencyProblem(
			"DCB could not use any agency code to find an agency.",
			"This problem was triggered because both the home library code and the default agency code were unresolvable.",
			systemCode, homeLibraryCode, null));
	}

	UnableToResolveAgencyProblem(String title, String detail,
		String systemCode, String homeLibraryCode, String defaultAgencyCode) {

		super(
			URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			title,
			(StatusType)null,
			detail,
			null,
			null,
			determineParameters(systemCode, homeLibraryCode, defaultAgencyCode)
		);
	}

	private static Map<String, Object> determineParameters(String systemCode,
		String homeLibraryCode, String defaultAgencyCode) {

		return Map.of(
			"systemCode", systemCode,
			"homeLibraryCode", homeLibraryCode,
			"defaultAgencyCode", defaultAgencyCode
		);
	}
}

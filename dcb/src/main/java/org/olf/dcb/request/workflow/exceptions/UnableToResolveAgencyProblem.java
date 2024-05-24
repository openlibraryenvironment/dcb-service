package org.olf.dcb.request.workflow.exceptions;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.StatusType;

import java.net.URI;
import java.util.Map;

public class UnableToResolveAgencyProblem extends AbstractThrowableProblem {
	public UnableToResolveAgencyProblem(String title, String detail,
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

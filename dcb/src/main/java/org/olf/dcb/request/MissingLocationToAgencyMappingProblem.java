package org.olf.dcb.request;

import static java.util.Collections.emptyMap;

import java.net.URI;

import org.zalando.problem.AbstractThrowableProblem;

import lombok.Getter;

@Getter
public class MissingLocationToAgencyMappingProblem extends AbstractThrowableProblem {
	private final String pickupLocationIdentifier;

	public MissingLocationToAgencyMappingProblem(String pickupLocationContext, String pickupLocationIdentifier) {
		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			"No location to agency mapping found for: context: \"%s\"; location: \"%s\""
				.formatted(pickupLocationContext,  pickupLocationIdentifier),
			null, null, null, null, emptyMap());

		this.pickupLocationIdentifier = pickupLocationIdentifier;
	}
}

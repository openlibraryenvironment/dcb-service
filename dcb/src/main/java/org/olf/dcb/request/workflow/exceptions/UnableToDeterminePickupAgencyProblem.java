package org.olf.dcb.request.workflow.exceptions;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.net.URI;
import java.util.Map;

import org.zalando.problem.AbstractThrowableProblem;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class UnableToDeterminePickupAgencyProblem extends AbstractThrowableProblem {
	String pickupLocationIdentifier;

	public UnableToDeterminePickupAgencyProblem(String pickupLocationContext, String pickupLocationIdentifier) {
		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			"Unable to determine pickup agency for: context: \"%s\"; location: \"%s\""
				.formatted(pickupLocationContext,  pickupLocationIdentifier),
			null, null, null, null,
			determineParameters(pickupLocationContext, pickupLocationIdentifier));

		this.pickupLocationIdentifier = pickupLocationIdentifier;
	}

	private static Map<String, Object> determineParameters(
		String pickupLocationContext, String pickupLocationIdentifier) {

		return Map.of(
			"pickupLocationContext", getValue(pickupLocationContext, "Unknown"),
			"pickupLocationIdentifier", getValue(pickupLocationIdentifier, "Unknown")
		);
	}
}

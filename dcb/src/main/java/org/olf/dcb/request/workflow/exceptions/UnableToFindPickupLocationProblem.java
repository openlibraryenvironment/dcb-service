package org.olf.dcb.request.workflow.exceptions;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.net.URI;
import java.util.Map;

import org.zalando.problem.AbstractThrowableProblem;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class UnableToFindPickupLocationProblem extends AbstractThrowableProblem {
	String pickupLocationId;

	public UnableToFindPickupLocationProblem(String pickupLocationId) {
		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			"Unable to find pickup location \"%s\"".formatted(pickupLocationId),
			null, null, null, null,
			Map.of("pickupLocationId", getValue(pickupLocationId, "Unknown")));

		this.pickupLocationId = pickupLocationId;
	}
}

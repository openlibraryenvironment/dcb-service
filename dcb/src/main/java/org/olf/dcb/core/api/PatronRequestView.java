package org.olf.dcb.core.api;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Value;

@Serdeable
@Value
public class PatronRequestView {
	UUID id;
	Citation citation;
	PickupLocation pickupLocation;
	Requestor requestor;
	String requesterNote;

	static PatronRequestView from(PatronRequest patronRequest) {
		final var identity = Identity.fromList(patronRequest.getPatron().getPatronIdentities());

		return new PatronRequestView(patronRequest.getId(),
				new Citation(patronRequest.getBibClusterId()),
				new PickupLocation(patronRequest.getPickupLocationCode()),
				new Requestor(identity.getLocalId(), identity.getLocalSystemCode(),
					patronRequest.getPatron().getHomeLibraryCode()),
				patronRequest.getRequesterNote());
	}

	@Serdeable
	@Value
	public static class PickupLocation {
		String code;
	}

	@Serdeable
	@Value
	public static class Citation {
		UUID bibClusterId;
	}

	@Serdeable
	@Value
	public static class Identity {
		String localId;
		String localSystemCode;

		private static Identity from(PatronIdentity patronIdentity) {
			return new Identity(patronIdentity.getLocalId(), patronIdentity.getHostLms().getCode());
		}

		public static Identity fromList(List<PatronIdentity> patronIdentities) {
			return patronIdentities.stream()
				.filter(PatronIdentity::getHomeIdentity)
				.findFirst()
				.map(Identity::from)
				.orElseThrow();
		}
	}

	@Serdeable
	@Value
	public static class Requestor {
		String localId;
		String localSystemCode;
		String homeLibraryCode;
	}
}

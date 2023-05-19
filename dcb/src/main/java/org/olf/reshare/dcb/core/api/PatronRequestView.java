package org.olf.reshare.dcb.core.api;

import java.util.List;
import java.util.UUID;

import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.core.model.PatronRequest;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record PatronRequestView(UUID id, Citation citation, PickupLocation pickupLocation,
	Requestor requestor) {

	static PatronRequestView from(PatronRequest patronRequest) {
		final var identity = Identity.fromList(patronRequest.getPatron().getPatronIdentities());

		return new PatronRequestView(patronRequest.getId(),
			new Citation(patronRequest.getBibClusterId()),
			new PickupLocation(patronRequest.getPickupLocationCode()),
			new Requestor(identity.localId, identity.localSystemCode,
				patronRequest.getPatron().getHomeLibraryCode()));
	}

	@Serdeable
	record PickupLocation(String code) { }

	@Serdeable
	record Citation(UUID bibClusterId) { }

	@Serdeable
	public record Identity(String localId, String localSystemCode) {

		private static Identity from(PatronIdentity patronIdentity) {
			return new Identity(patronIdentity.getLocalId(), patronIdentity.getHostLms().code);
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
	record Requestor(String localId, String localSystemCode, String homeLibraryCode) { }
}

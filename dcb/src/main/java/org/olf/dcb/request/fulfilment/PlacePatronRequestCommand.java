package org.olf.dcb.request.fulfilment;

import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Serdeable
@Value
public class PlacePatronRequestCommand {
	@NotNull Citation citation;
	@NotNull PickupLocation pickupLocation;
	@NotNull Requestor requestor;
	@Nullable String description;

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
	public static class Requestor {
		String localId;
		String localSystemCode;
		String homeLibraryCode;

		public Requestor(String localId, String localSystemCode, String homeLibraryCode) {
			this.localId = localId;
			this.localSystemCode = localSystemCode;
			this.homeLibraryCode = homeLibraryCode;
		}
	}
}

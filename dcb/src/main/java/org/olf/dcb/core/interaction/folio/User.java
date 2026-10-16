package org.olf.dcb.core.interaction.folio;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Builder
@Data
public class User {
	@Nullable String id;
	@Nullable String patronGroupName;
	@Nullable String barcode;
	@Nullable String username;
	/**
	 * The identifier edge-patron looks a patron up by, and the ONLY one it accepts - it
	 * queries mod-users on this field and has no fallback to a FOLIO user id. Empty here
	 * means this library cannot answer for its own patron accounts.
	 */
	@Nullable String externalSystemId;
	@Nullable User.PersonalDetails personal;
	@Nullable Boolean blocked;
	@Nullable Boolean active;
	@Nullable String expirationDate;

	@Serdeable
	@Builder
	@Data
	public static class PersonalDetails {
		@Nullable String firstName;
		@Nullable String lastName;
		@Nullable String middleName;
		@Nullable String preferredFirstName;
	}
}

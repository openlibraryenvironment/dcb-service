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
	@Nullable User.PersonalDetails personal;
	@Nullable Boolean blocked;
	@Nullable Boolean active;

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

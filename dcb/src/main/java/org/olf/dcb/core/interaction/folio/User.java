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
	@Nullable String patronGroup;
	@Nullable String barcode;
	@Nullable Personal personal;

	@Serdeable
	@Builder
	@Data
	public static class Personal {
		@Nullable String firstName;
		@Nullable String lastName;
		@Nullable String middleName;
		@Nullable String preferredFirstName;
	}
}

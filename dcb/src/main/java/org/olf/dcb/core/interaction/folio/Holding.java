package org.olf.dcb.core.interaction.folio;

import java.time.Instant;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Serdeable
@Builder
@Value
public class Holding {
	@Nullable String id;
	@Nullable String barcode;
	@Nullable String callNumber;
	@Nullable String location;
	@Nullable String locationCode;
	@Nullable String status;
	@Nullable Instant dueDate;
	@Nullable String permanentLoanType;
	@Nullable MaterialType materialType;
}

package org.olf.reshare.dcb.request.resolution;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Serdeable
public record SupplierRequest(@Nullable UUID id,
	@NotNull @NotBlank Holdings.Item item,
	@NotNull @NotBlank Holdings.Agency agency
  ) {}

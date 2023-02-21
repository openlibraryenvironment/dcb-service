package org.olf.reshare.dcb.request.resolution;

import io.micronaut.serde.annotation.Serdeable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Serdeable
public record Holdings(Agency agency,
	List<Item> items) {
		@Serdeable
		public record Agency(String code) { }
		@Serdeable
		public record Item(UUID id) { }
}

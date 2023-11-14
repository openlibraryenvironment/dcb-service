package org.olf.dcb.core.interaction.folio;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Builder
@Data
public class RtacError {
	String message;
	String code;
}

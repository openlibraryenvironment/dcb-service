package org.olf.dcb.core.api.serde;

import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class HostLmsDTO {
	UUID id;
	String code;
}

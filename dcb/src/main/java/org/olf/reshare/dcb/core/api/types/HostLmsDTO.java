package org.olf.reshare.dcb.core.api.types;

import java.util.UUID;
import org.olf.reshare.dcb.core.model.Location;
import io.micronaut.serde.annotation.Serdeable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class HostLmsDTO {
	UUID id;
	String code;
}

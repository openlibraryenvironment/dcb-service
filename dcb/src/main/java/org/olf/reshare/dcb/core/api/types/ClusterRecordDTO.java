package org.olf.reshare.dcb.core.api.types;

import java.util.UUID;
import org.olf.reshare.dcb.core.model.Location;
import io.micronaut.serde.annotation.Serdeable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class ClusterRecordDTO {

	UUID clusterId;
	String title;
	List<BibRecordDTO> bibs;

}

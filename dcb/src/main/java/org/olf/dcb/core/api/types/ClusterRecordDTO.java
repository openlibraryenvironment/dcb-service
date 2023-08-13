package org.olf.dcb.core.api.types;

import java.util.List;
import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class ClusterRecordDTO {

	UUID clusterId;
	String dateCreated;
	String dateUpdated;
	String title;
	UUID selectedBibId;
	BibRecordDTO selectedBib;
	List<ClusterBibDTO> bibs;
}

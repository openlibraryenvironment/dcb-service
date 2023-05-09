package org.olf.reshare.dcb.core.api.types;

import java.util.UUID;
import java.util.Map;
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
public class BibRecordDTO {

	UUID bibId;
	String title;
	String sourceRecordId;
	UUID sourceSystemId;
	String sourceSystemCode;
	String recordStatus;
	String typeOfRecord;
	String derivedType;
	Map<String,Object> canonicalMetadata;

}

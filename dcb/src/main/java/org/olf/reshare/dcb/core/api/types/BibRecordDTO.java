package org.olf.reshare.dcb.core.api.types;

import java.util.Map;
import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

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

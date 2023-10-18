package org.olf.dcb.core.api.serde;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.olf.dcb.core.model.BibRecord;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Builder
@Data
@AllArgsConstructor(onConstructor_ = @Creator())
@Serdeable
@Accessors(chain = true)
public class BibRecordDTO {
	
	
	public BibRecordDTO( final BibRecord br ) {
		
		this
			.setBibId(br.getId())
			.setTitle(br.getTitle())
			.setSourceRecordId(br.getSourceRecordId())
			.setSourceSystemId(br.getSourceSystemId())
			.setSourceSystemCode(Objects.toString(br.getSourceSystemId(), null))
			.setRecordStatus(br.getRecordStatus())
			.setTypeOfRecord(br.getTypeOfRecord())
			.setDerivedType(br.getDerivedType())
			.setCanonicalMetadata(br.getCanonicalMetadata());
	}

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

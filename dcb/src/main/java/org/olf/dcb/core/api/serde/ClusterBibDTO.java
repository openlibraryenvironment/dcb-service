package org.olf.dcb.core.api.serde;

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
@Accessors(chain = true)
@Serdeable
public class ClusterBibDTO {

	public ClusterBibDTO( final BibRecord bib ) {
		
		this.setBibId(bib.getId())
			.setTitle(bib.getTitle())
			.setSourceRecordId(bib.getSourceRecordId())
			.setSourceSystem(Objects.toString(bib.getSourceSystemId(), null))
			.setMetadataScore("" + bib.getMetadataScore())
			.setClusterReason(bib.getClusterReason());
	}
	
	UUID bibId;
	String title;
	String sourceRecordId;
	String sourceSystem;
	String metadataScore;
	String clusterReason;
}

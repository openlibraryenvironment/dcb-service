package org.olf.dcb.core.api.serde;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.olf.dcb.core.model.clustering.ClusterRecord;

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
public class ClusterRecordDTO {
	
	public ClusterRecordDTO( final ClusterRecord cr ) {
		this
			.setDateUpdated(cr.getDateUpdated().toString())
			.setDateCreated(cr.getDateCreated().toString())
			.setDeleted(Boolean.TRUE.equals(cr.getIsDeleted()))
			.setClusterId(cr.getId())
			.setTitle(cr.getTitle())
			.setSelectedBibId(cr.getSelectedBib())
		
		// Also set bibs and the selected bib from the bib collection.
			.setBibs(
				Stream.ofNullable(cr.getBibs())
					.flatMap(Set::stream)
					.map( bib -> {
						if ( bib.getId().equals(cr.getSelectedBib()) ) {
							this.setSelectedBib( new BibRecordDTO(bib) );
						}
						
						return bib;
					})
					.map(ClusterBibDTO::new)
					.toList());
		;
	}

	UUID clusterId;
	String dateCreated;
	String dateUpdated;
	String title;
	UUID selectedBibId;
	BibRecordDTO selectedBib;
	boolean isDeleted;
	List<ClusterBibDTO> bibs;
}

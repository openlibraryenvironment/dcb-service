package org.olf.dcb.core.interaction.polaris;

import java.time.Instant;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Serdeable
public class ExtendedBibsPagedGetParams {

  private Instant startdatemodified;
  private Integer lastId;
  private Integer pagesInCurrentCheckpoint;
  private Instant checkpointDate;
  private Integer nrecs;
  private Integer recordsInLastPage;
	private Instant highestDateUpdatedSeen;
  private final String cpType="POLARIS";
  private String hostCode;

	public BibsPagedGetParams toBibsPagedGetParams() {
		return BibsPagedGetParams
			.builder()
			.startdatemodified(this.getStartdatemodified())
			.lastId(this.getLastId())
			.nrecs(this.getNrecs())
			.build();
	}
}

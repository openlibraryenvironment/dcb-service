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
	// When this id walk began, by DCB's clock. A record edited behind the cursor during the walk is
	// older than the highest date seen, so the next watermark must not pass this.
	private Instant walkStartedAt;
	// Polaris deletions are read on their own watermark and cursor; see PolarisLmsClient.readDeletions
	private Instant deletedSince;
	private Integer deletedLastId;
	// The last failed read of deletions, so a server that refuses them is asked again every 30 minutes, not every walk
	private Instant deletionsFailedAt;
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

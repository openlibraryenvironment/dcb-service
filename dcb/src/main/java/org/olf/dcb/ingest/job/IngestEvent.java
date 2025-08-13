package org.olf.dcb.ingest.job;

import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class IngestEvent {

	private String eventType;
}

package org.olf.dcb.ingest.conversion;

import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.ingest.model.IngestRecord;

import reactor.core.publisher.Mono;

public interface SourceToIngestRecordConverter {
  Mono<IngestRecord> convertSourceToIngestRecord( SourceRecord source );
}

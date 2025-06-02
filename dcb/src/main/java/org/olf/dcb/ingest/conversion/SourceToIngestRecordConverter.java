package org.olf.dcb.ingest.conversion;

import org.olf.dcb.core.error.DcbException;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.ingest.model.IngestRecord;

public interface SourceToIngestRecordConverter {
	IngestRecord convertSourceToIngestRecord( SourceRecord source ) throws DcbException;
}

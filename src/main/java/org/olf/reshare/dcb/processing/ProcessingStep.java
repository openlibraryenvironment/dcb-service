package org.olf.reshare.dcb.processing;

import java.util.function.BiFunction;

import org.olf.reshare.dcb.ingest.IngestRecordDef;
import org.olf.reshare.dcb.model.BibRecord;

public interface ProcessingStep extends BiFunction<BibRecord, IngestRecordDef, BibRecord> {
	
}

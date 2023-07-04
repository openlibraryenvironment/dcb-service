package org.olf.dcb.processing;

import java.util.function.BiFunction;

import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.ingest.model.IngestRecord;

public interface ProcessingStep extends BiFunction<BibRecord, IngestRecord, BibRecord> {

}

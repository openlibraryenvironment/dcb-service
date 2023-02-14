package org.olf.reshare.dcb.processing;

import java.util.function.BiFunction;

import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord;

public interface ProcessingStep extends BiFunction<BibRecord, IngestRecord, BibRecord> {

}

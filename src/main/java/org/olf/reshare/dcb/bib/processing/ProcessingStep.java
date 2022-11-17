package org.olf.reshare.dcb.bib.processing;

import java.util.function.BiFunction;

import org.olf.reshare.dcb.ImportedRecord;
import org.olf.reshare.dcb.bib.model.BibRecord;

public interface ProcessingStep extends BiFunction<BibRecord, ImportedRecord, BibRecord> {
	
}

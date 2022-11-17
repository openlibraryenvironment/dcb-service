package org.olf.reshare.dcb.bib;

import org.olf.reshare.dcb.ImportedRecord;
import org.olf.reshare.dcb.bib.model.BibRecord;
import org.reactivestreams.Publisher;

public interface BibRecordService {
//	public void addBibRecord(ImportedRecord record);

	public void cleanup();

	public void commit();

	public Publisher<BibRecord> process(Publisher<ImportedRecord> source);
}

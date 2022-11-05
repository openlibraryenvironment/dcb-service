package org.olf.reshare.dcb.bib;

import org.olf.reshare.dcb.ImportedRecord;

public interface BibRecordService {
	public void addBibRecord( ImportedRecord record );

	public void cleanup ();

	public void commit ();	
}

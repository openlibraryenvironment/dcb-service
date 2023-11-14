package org.olf.dcb.indexing.model;

import java.util.UUID;

import org.olf.dcb.core.model.BibRecord;

import io.micronaut.serde.annotation.Serdeable;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Serdeable
@ExcludeFromGeneratedCoverageReport
public class OpenSearchNestedBibIndexDoc {

	private final boolean primary;
	private final BibRecord bib;

	protected OpenSearchNestedBibIndexDoc(BibRecord bib, boolean primary) {
		this.bib = bib;
		this.primary = primary;
	}

	public OpenSearchNestedBibIndexDoc(BibRecord bib) {
		this(bib, false);
	}

	UUID getBibId() {
		return bib.getId();
	}

	String getTitle() {
		return bib.getTitle();
	}

	UUID getSourceSystem() {
		return bib.getSourceSystemId();
	}

	String getSourceRecordId() {
		return bib.getSourceRecordId();
	}

	boolean isPrimary() {
		return primary;
	}
}

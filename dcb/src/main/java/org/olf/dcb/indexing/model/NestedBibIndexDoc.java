package org.olf.dcb.indexing.model;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Stream;

import org.olf.dcb.availability.job.BibAvailabilityCount;
import org.olf.dcb.core.model.BibRecord;

import io.micronaut.serde.annotation.Serdeable;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Serdeable
@ExcludeFromGeneratedCoverageReport
public class NestedBibIndexDoc {

	private final boolean primary;
	private final BibRecord bib;
	private final String hostLmsCode;
	private final Collection<BibAvailabilityCount> bibAvailabilityCounts;

	protected NestedBibIndexDoc(BibRecord bib, String hostLmsCode, boolean primary, Collection<BibAvailabilityCount> bibAvailabilityCounts) {
		this.bib = bib;
		this.primary = primary;
		this.hostLmsCode = hostLmsCode;
		this.bibAvailabilityCounts = bibAvailabilityCounts;
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

	String getSourceSystemCode() {
		return hostLmsCode;
	}
	
	Collection<AvailabilityEntry> getAvailability() {
		return Stream.ofNullable(bibAvailabilityCounts)
				.flatMap(Collection::stream)
				.map( count -> {
					String code = count.getInternalLocationCode();
					return new AvailabilityEntry(code != null ? code : count.getRemoteLocationCode(), count.getCount());
				})
				.toList();
	}
	
	@Serdeable
	protected static record AvailabilityEntry(
			String location,
			int count ) {
	}
}

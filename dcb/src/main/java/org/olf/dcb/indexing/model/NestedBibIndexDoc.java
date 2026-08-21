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

	public UUID getBibId() {
		return bib.getId();
	}

	public String getTitle() {
		return bib.getTitle();
	}

	public UUID getSourceSystem() {
		return bib.getSourceSystemId();
	}

	public String getSourceRecordId() {
		return bib.getSourceRecordId();
	}

	public boolean isPrimary() {
		return primary;
	}

	public String getSourceSystemCode() {
		return hostLmsCode;
	}
	
	public Collection<AvailabilityEntry> getAvailability() {
		return Stream.ofNullable(bibAvailabilityCounts)
				.flatMap(Collection::stream)
				.map( count -> {
					String code = count.getInternalLocationCode();
					String location = count.getRemoteLocationCode();

					return new AvailabilityEntry(code, location, code+"."+location, count.getCount());
				})
				.toList();
	}
	
	@Serdeable
	public static record AvailabilityEntry(
			String library,
			String location,
			String combined,
			int count ) {
	}
}

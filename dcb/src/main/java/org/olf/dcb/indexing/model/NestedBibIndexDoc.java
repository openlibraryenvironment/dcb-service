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
	
	/**
	 * The agency a location resolved to, and the location code the ILS reported.
	 * <p>
	 * BibAvailabilityCount calls the first of these internalLocationCode, but
	 * AvailabilityCheckJob fills it from the Location-to-AGENCY mapping, so it is an agency
	 * code. Agency rather than Host LMS is the library identity that holds on a shared
	 * system, where one tenant fronts several libraries; the Host LMS code is already on
	 * this document as sourceSystemCode. The index field keeps the name {@code library}
	 * because dcb-locate aggregates on it.
	 */
	public Collection<AvailabilityEntry> getAvailability() {
		return Stream.ofNullable(bibAvailabilityCounts)
				.flatMap(Collection::stream)
				.map( count -> {
					String agency = count.getInternalLocationCode();
					String location = count.getRemoteLocationCode();

					return new AvailabilityEntry(agency, location, combined(agency, location), count.getCount());
				})
				.toList();
	}

	// An unmapped location has no agency, and concatenating that null indexed the string "null."
	private static String combined(String agency, String location) {
		return agency == null || location == null ? null : agency + "." + location;
	}
	
	@Serdeable
	/**
	 * {@code library} carries an AGENCY code, despite the name.
	 * <p>
	 * It is not renamed: {@code dcb-locate} aggregates and filters on
	 * {@code members.availability.library.keyword} (Facet.java, CqlInterpreter.java), so the
	 * name is a published contract with another repository. See docs/shared-index.md, which
	 * records the six-day crash-loop this field has already caused.
	 */
	public static record AvailabilityEntry(
			String library,
			String location,
			String combined,
			int count ) {
	}
}

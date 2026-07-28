package org.olf.dcb.core.interaction;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class Bib {
	private String title;
	private String author;

	// Circulation axis - drives loan periods and renewal limits, not bibliographic format
	private String canonicalItemType;

	// Bibliographic axis - the source record's MARC leader position 06 ('a', 'g', 'i', 'j', ...)
	// This is what determines the material type of a virtual bib at the host LMS
	private String typeOfRecord;
}

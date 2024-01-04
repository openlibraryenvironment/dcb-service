package org.olf.dcb.core.interaction;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import lombok.experimental.Accessors;
import lombok.Data;
import java.util.List;
import java.util.Date;

/**
 *
 */
@Builder
@Data
@Accessors(chain=true)
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class Patron {
	private List<String> localId;
	private List<String> localNames;
	private List<String> localBarcodes;
	private List<String> uniqueIds;
	private String localPatronType;
	private String localHomeLibraryCode;

	// Because different ILS systems will have different needs for converting local patron type
	// into a canonical type, Sierra for example uses a numeric range mapping, we should hide the
	// details of how we convert a local patron type to a canonical type in the HostILS implementation
	// and pass back the mapped type here, rather than forcing the caller to know how to convert.
	private String canonicalPatronType;
	private Date expiryDate; // To be formatted as needed by any system
	private String localItemId;
	private Integer localItemLocationId;
}

package org.olf.dcb.core.interaction;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import java.util.List;

/**
 *
 */


@Builder
@Data
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
	private String localPatronAgency;
}

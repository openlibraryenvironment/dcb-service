package org.olf.dcb.request.resolution;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Data
@ExcludeFromGeneratedCoverageReport
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
public class SupplyCandidateItem {
	private Location pickupLocation;
	private Item item;
	private double distance;
}


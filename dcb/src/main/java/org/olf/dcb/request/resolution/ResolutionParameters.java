package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static org.olf.dcb.request.resolution.SharedIndexService.INCLUDE_DELETED_CLUSTER_RECORDS_DEFAULT;

import java.util.List;
import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
@Serdeable
public class ResolutionParameters {
	String borrowingAgencyCode;
	String borrowingHostLmsCode;
	UUID bibClusterId;
	@Builder.Default Boolean includeDeletedClusterRecords = INCLUDE_DELETED_CLUSTER_RECORDS_DEFAULT;
	String pickupLocationCode;
	String pickupAgencyCode;
	@Builder.Default List<String> excludedSupplyingAgencyCodes = emptyList();
	ManualItemSelection manualItemSelection;
	Boolean isExpeditedCheckout;
}

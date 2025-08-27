package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ResolutionParameters {
	UUID bibClusterId;
	Patron patron;
	String pickupLocationCode;
	@Builder.Default List<String> excludedAgencyCodes = emptyList();
	String patronHostLmsCode;
	ManualItemSelection manualItemSelection;

	public static ResolutionParameters parametersFor(PatronRequest patronRequest, List<String> excludedAgencyCodes) {
		return builder()
			.patron(getValueOrNull(patronRequest, PatronRequest::getPatron))
			.bibClusterId(getValueOrNull(patronRequest, PatronRequest::getBibClusterId))
			.pickupLocationCode(getValueOrNull(patronRequest, PatronRequest::getPickupLocationCode))
			.excludedAgencyCodes(excludedAgencyCodes)
			.patronHostLmsCode(getValueOrNull(patronRequest, PatronRequest::getPatronHostlmsCode))
			.manualItemSelection(ManualItemSelection.builder()
				.isManuallySelected(getValue(patronRequest, PatronRequest::getIsManuallySelectedItem, false))
				.localItemId(getValueOrNull(patronRequest, PatronRequest::getLocalItemId))
				.hostLmsCode(getValueOrNull(patronRequest, PatronRequest::getLocalItemHostlmsCode))
				.agencyCode(getValueOrNull(patronRequest, PatronRequest::getLocalItemAgencyCode))
				.build())
			.build();
	}
}

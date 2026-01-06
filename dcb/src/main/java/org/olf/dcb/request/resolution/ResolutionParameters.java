package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;

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
	String pickupLocationCode;
	@Builder.Default List<String> excludedSupplyingAgencyCodes = emptyList();
	ManualItemSelection manualItemSelection;

	public static ResolutionParameters parametersFor(PatronRequest patronRequest,
		List<String> excludedSupplyingAgencyCodes) {

		final var patron = getValueOrNull(patronRequest, PatronRequest::getPatron);

		return builder()
			.borrowingAgencyCode(getValueOrNull(patron, Patron::determineAgencyCode))
			.borrowingHostLmsCode(getValueOrNull(patronRequest, PatronRequest::getPatronHostlmsCode))
			.bibClusterId(getValueOrNull(patronRequest, PatronRequest::getBibClusterId))
			.pickupLocationCode(getValueOrNull(patronRequest, PatronRequest::getPickupLocationCode))
			.excludedSupplyingAgencyCodes(excludedSupplyingAgencyCodes)
			.manualItemSelection(ManualItemSelection.builder()
				.isManuallySelected(getValue(patronRequest, PatronRequest::getIsManuallySelectedItem, false))
				.localItemId(getValueOrNull(patronRequest, PatronRequest::getLocalItemId))
				.hostLmsCode(getValueOrNull(patronRequest, PatronRequest::getLocalItemHostlmsCode))
				.agencyCode(getValueOrNull(patronRequest, PatronRequest::getLocalItemAgencyCode))
				.build())
			.build();
	}
}

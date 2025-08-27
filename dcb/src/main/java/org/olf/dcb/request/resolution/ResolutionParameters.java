package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Patron;

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
}

package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.Objects;

import org.olf.dcb.core.model.Item;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;

@Builder
@Value
@ToString
@Serdeable
public class ManualItemSelection {
	Boolean isManuallySelected;
	String localItemId;
	String agencyCode;
	String hostLmsCode;

	void validate() {
		if (localItemId == null) {
			throw new IllegalArgumentException("Local item ID is required for manual item selection");
		}
		if (hostLmsCode == null) {
			throw new IllegalArgumentException("Host LMS code is required for manual item selection");
		}
		if (agencyCode == null) {
			throw new IllegalArgumentException("Agency code is required for manual item selection");
		}
	}

	public Boolean matches(Item item) {
		return isLocalId(item) && isAgencyCode(item) && isHostLmsCode(item);
	}

	private Boolean isLocalId(Item item) {
		return Objects.equals(getValueOrNull(item, Item::getLocalId), localItemId);
	}

	private Boolean isAgencyCode(Item item) {
		return Objects.equals(getValueOrNull(item, Item::getAgencyCode), agencyCode);
	}

	private Boolean isHostLmsCode(Item item) {
		return Objects.equals(getValueOrNull(item, Item::getHostLmsCode), hostLmsCode);
	}
}

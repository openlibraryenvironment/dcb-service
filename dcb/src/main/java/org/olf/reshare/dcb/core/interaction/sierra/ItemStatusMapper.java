package org.olf.reshare.dcb.core.interaction.sierra;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.UNKNOWN;

import java.util.Objects;

import org.olf.reshare.dcb.core.model.ItemStatus;

/**
Status is interpreted based upon
 <a href="https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html#item%20STATUS">
 this documentation</a>
 */
class ItemStatusMapper {
	ItemStatus mapStatus(services.k_int.interaction.sierra.items.Status status) {
		final var AVAILABLE_CODE = "-";

		if (status == null || isEmpty(status.getCode())) {
			return new ItemStatus(UNKNOWN);
		}

		if (Objects.equals(status.getCode(), AVAILABLE_CODE)) {
			if (isNotEmpty(status.getDuedate())) {
				return new ItemStatus(CHECKED_OUT);
			}

			return new ItemStatus(AVAILABLE);
		}
		else {
			return new ItemStatus(UNAVAILABLE);
		}
	}
}

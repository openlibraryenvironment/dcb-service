package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.Objects;

import jakarta.inject.Singleton;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ManualSelection {

	public Item chooseItem(Resolution resolution) {
		PatronRequest request = resolution.getPatronRequest();

		validateManualSelectionFor(request);

		final var patronRequestId = getValueOrNull(request, PatronRequest::getId);
		final var items = resolution.getAllItems();

		log.info("PR-{} - chooseItem(array of size {})", patronRequestId, items.size());

		return items.stream()
			.peek(item -> log.info(
				"PR-{} - Consider item {} @ {}",
				patronRequestId,
				item.getLocalId(),
				item.getLocation()
			))
			.filter(item -> manuallySelected(request, item))
			.findFirst()
			.orElse(null);
	}

	static void validateManualSelectionFor(PatronRequest patronRequest) {
		if (patronRequest.getLocalItemId() == null) {
			throw new IllegalArgumentException("localItemId is required for manual item selection");
		}
		if (patronRequest.getLocalItemHostlmsCode() == null) {
			throw new IllegalArgumentException("localItemHostlmsCode is required for manual item selection");
		}
		if (patronRequest.getLocalItemAgencyCode() == null) {
			throw new IllegalArgumentException("localItemAgencyCode is required for manual item selection");
		}
	}

	private static Boolean manuallySelected(PatronRequest patronRequest, Item item) {
		log.debug("Filtering manually selected item: {} for patron request: {}", item, patronRequest);

		return isLocalId(patronRequest, item) &&
			isAgencyCode(patronRequest, item) &&
			isHostlmsCode(patronRequest, item);
	}

	private static Boolean isLocalId(PatronRequest patronRequest, Item item) {
		return Objects.equals(item.getLocalId(), patronRequest.getLocalItemId());
	}

	private static Boolean isAgencyCode(PatronRequest patronRequest, Item item) {
		return Objects.equals(item.getAgencyCode(), patronRequest.getLocalItemAgencyCode());
	}

	private static Boolean isHostlmsCode(PatronRequest patronRequest, Item item) {
		return Objects.equals(item.getHostLmsCode(), patronRequest.getLocalItemHostlmsCode());
	}
}

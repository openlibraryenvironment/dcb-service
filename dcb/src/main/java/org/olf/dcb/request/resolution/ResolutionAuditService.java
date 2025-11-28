package org.olf.dcb.request.resolution;

import static org.olf.dcb.request.workflow.PresentableItem.toPresentableItem;
import static org.olf.dcb.request.workflow.PresentableItem.toPresentableItems;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.MapUtils.putNonNullValue;

import java.util.HashMap;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;

import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

@Singleton
@AllArgsConstructor
public class ResolutionAuditService {
	private final PatronRequestAuditService patronRequestAuditService;

	public Mono<Resolution> auditResolution(Resolution resolution,
		PatronRequest patronRequest, String processName) {

		final var successful = getValue(resolution, Resolution::successful, false);

		return successful
			? auditSuccessfulResolution(resolution, patronRequest, processName)
			: auditUnsuccessfulResolution(resolution, patronRequest, processName);
	}

	private Mono<Resolution> auditSuccessfulResolution(Resolution resolution,
		PatronRequest patronRequest, String processName) {

		final var auditData = new HashMap<String, Object>();

		final var chosenItem = getValueOrNull(resolution, Resolution::getChosenItem);

		putNonNullValue(auditData, "selectedItem", toPresentableItem(chosenItem));

		addItemCollectionsToAuditData(resolution, auditData);

		return patronRequestAuditService.addAuditEntry(patronRequest,
				formatResolutionAuditMessage(processName, chosenItem), auditData)
			.thenReturn(resolution);
	}

	private String formatResolutionAuditMessage(String processName, Item chosenItem) {
		final var localId = getValue(chosenItem, Item::getLocalId, "null");
		final var supplyingHostLmsCode = getValue(chosenItem, Item::getHostLmsCode, "null");

		return ("%s selected an item with local ID \"%s\" from Host LMS \"%s\"")
			.formatted(processName, localId, supplyingHostLmsCode);
	}

	private Mono<Resolution> auditUnsuccessfulResolution(Resolution resolution,
		PatronRequest patronRequest, String processName) {

		final var auditData = new HashMap<String, Object>();

		addItemCollectionsToAuditData(resolution, auditData);

		final var message = "%s could not select an item".formatted(processName);

		return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
			.thenReturn(resolution);
	}

	private void addItemCollectionsToAuditData(Resolution resolution, HashMap<String, Object> auditData) {
		putNonNullValue(auditData, "filteredItems", toPresentableItems(resolution.getFilteredItems()));
		putNonNullValue(auditData, "sortedItems", toPresentableItems(resolution.getSortedItems()));
		putNonNullValue(auditData, "allItems", toPresentableItems(resolution.getAllItems()));
	}
}

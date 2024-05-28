package org.olf.dcb.request.resolution;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

@Slf4j
@Singleton
public class ManualSelectionStrategy implements ResolutionStrategy{

	@Override
	public String getCode() {
		return MANUAL_SELECTION;
	}

	@Override
	public Mono<Item> chooseItem(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest) {
		final var patronRequestId = getValue(patronRequest, PatronRequest::getId);

		log.info("PR-{} - chooseItem(array of size {})", patronRequestId, items.size());

		return Mono.justOrEmpty(
			items.stream()
				.peek(item -> log.info(
					"PR-{} - Consider item {} @ {} requestable:{} holds:{} ",
					patronRequestId,
					item.getLocalId(),
					item.getLocation(),
					item.getIsRequestable(),
					item.hasNoHolds()))
				.filter(Item::getIsRequestable)
				.filter(Item::hasNoHolds)
				.filter(item -> manuallySelected(patronRequest, item))
				.findFirst()
		);
	}

	private static Boolean manuallySelected(PatronRequest patronRequest, Item item) {
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

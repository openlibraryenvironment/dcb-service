package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ManualSelectionStrategy implements ResolutionStrategy {

	@Override
	public String getCode() {
		return MANUAL_SELECTION;
	}

	@Override
	public Mono<Item> chooseItem(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest) {
		final var patronRequestId = getValueOrNull(patronRequest, PatronRequest::getId);

		log.info("PR-{} - chooseItem(array of size {})", patronRequestId, items.size());

		return Mono.justOrEmpty(
			items.stream()
				.peek(item -> log.info(
					"PR-{} - Consider item {} @ {} holds:{} ",
					patronRequestId,
					item.getLocalId(),
					item.getLocation(),
					item.hasNoHolds()))
				.filter(Item::hasNoHolds)
				.filter(item -> manuallySelected(patronRequest, item))
				.findFirst()
		);
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

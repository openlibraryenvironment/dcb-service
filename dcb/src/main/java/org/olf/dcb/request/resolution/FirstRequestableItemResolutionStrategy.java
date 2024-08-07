package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class FirstRequestableItemResolutionStrategy implements ResolutionStrategy {
	@Override
	public String getCode() {
		return "FirstItem";
	}

	@Override
	public Mono<Item> chooseItem(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest) {
		final var patronRequestId = getValueOrNull(patronRequest, PatronRequest::getId);

		log.info("PR-{} - chooseItem(array of size {})", patronRequestId, items.size());

		return Mono.justOrEmpty(
			items.stream()
				.peek(item -> log.info(
						"PR-{} - Consider item {} @ {}",
					patronRequestId,
					item.getLocalId(),
					item.getLocation()
				))
				.findFirst()
		);
	}
}

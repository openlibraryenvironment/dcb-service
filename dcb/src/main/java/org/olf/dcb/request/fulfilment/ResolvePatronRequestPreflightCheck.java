package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failed;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Requires(property = "dcb.requests.preflight-checks.resolve-patron-request.enabled", defaultValue = "true", notEquals = "false")
public class ResolvePatronRequestPreflightCheck implements PreflightCheck {
	private final PatronRequestResolutionService patronRequestResolutionService;

	public ResolvePatronRequestPreflightCheck(
		PatronRequestResolutionService patronRequestResolutionService) {

		this.patronRequestResolutionService = patronRequestResolutionService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		return Mono.just(command)
			// This is a workaround due to the current resolution process
			// being too coupled to a patron request
			.map(this::mapToPatronRequest)
			.flatMap(patronRequestResolutionService::resolvePatronRequest)
			.map(this::checkResolution);
	}

	private List<CheckResult> checkResolution(Resolution resolution) {
		log.debug("checkResolution({})", resolution);

		final var chosenItem = getValue(resolution, Resolution::getChosenItem);

		if (chosenItem == null) {
			return List.of(failed("NO_ITEM_SELECTABLE_FOR_REQUEST",
				"Patron request for cluster record \"%s\" could not be resolved to an item"
					.formatted(resolution.getBibClusterId())
			));
		}

		return List.of(passed());
	}

	private PatronRequest mapToPatronRequest(PlacePatronRequestCommand command) {
		log.debug("mapToPatronRequest({})", command);

		return PatronRequest.builder()
			.id(UUID.randomUUID())
			.bibClusterId(command.getCitation().getBibClusterId())
			.requestedVolumeDesignation(command.getCitation().getVolumeDesignator())
			.pickupLocationCodeContext(command.getPickupLocationContext())
			.pickupLocationCode(command.getPickupLocationCode())
			.build();
	}
}

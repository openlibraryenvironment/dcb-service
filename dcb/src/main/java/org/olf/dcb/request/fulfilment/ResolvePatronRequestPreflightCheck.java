package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.passed;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;

import org.olf.dcb.request.resolution.PatronRequestResolutionService;

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
			.map(patronRequest -> List.of(passed()));
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

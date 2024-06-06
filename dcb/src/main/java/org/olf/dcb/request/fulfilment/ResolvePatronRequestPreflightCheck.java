package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failed;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static reactor.function.TupleUtils.function;

import java.util.List;

import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Requires(property = "dcb.requests.preflight-checks.resolve-patron-request.enabled", defaultValue = "true", notEquals = "false")
public class ResolvePatronRequestPreflightCheck implements PreflightCheck {
	private final PatronRequestResolutionService patronRequestResolutionService;
	private final BeanProvider<PatronRequestService> patronRequestServiceProvider;

	public ResolvePatronRequestPreflightCheck(
		PatronRequestResolutionService patronRequestResolutionService,
		BeanProvider<PatronRequestService> patronRequestServiceProvider) {

		this.patronRequestResolutionService = patronRequestResolutionService;
		this.patronRequestServiceProvider = patronRequestServiceProvider;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		// This is a workaround due to the current resolution process
		// being too coupled to a patron request
		return Mono.just(patronRequestServiceProvider.get())
			.zipWith(mapToPatron(command))
			.map(function((patronRequestService, patron) -> patronRequestService.mapToPatronRequest(command, patron)))
			.map(PatronRequestService.mapManualItemSelectionIfPresent(command))
			.flatMap(patronRequestResolutionService::resolvePatronRequest)
			.map(this::checkResolution);
	}

	private static Mono<Patron> mapToPatron(PlacePatronRequestCommand command) {
		final var homeIdentity = PatronIdentity.builder()
			.homeIdentity(true)
			.build();

		final var patron = Patron.builder()
			.patronIdentities(List.of(homeIdentity))
			.build();

		return Mono.just(patron);
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
}

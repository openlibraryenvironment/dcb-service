package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failed;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static reactor.function.TupleUtils.function;

import java.util.List;

import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.core.interaction.LocalPatronService;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveAgencyProblem;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

@Slf4j
@Singleton
@Requires(property = "dcb.requests.preflight-checks.resolve-patron-request.enabled", defaultValue = "true", notEquals = "false")
public class ResolvePatronRequestPreflightCheck implements PreflightCheck {
	private final PatronRequestResolutionService patronRequestResolutionService;
	private final BeanProvider<PatronRequestService> patronRequestServiceProvider;
	private final LocalPatronService localPatronService;

	public ResolvePatronRequestPreflightCheck(
		PatronRequestResolutionService patronRequestResolutionService,
		BeanProvider<PatronRequestService> patronRequestServiceProvider,
		LocalPatronService localPatronService) {

		this.patronRequestResolutionService = patronRequestResolutionService;
		this.patronRequestServiceProvider = patronRequestServiceProvider;
		this.localPatronService = localPatronService;
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
			.map(this::checkResolution)
			// Many of these errors are duplicated from the resolve patron preflight check
			// This is due to both having to resolve the patron before performing the checks
			.onErrorResume(UnableToResolveAgencyProblem.class, error -> agencyNotFound(error,
				getValue(command, PlacePatronRequestCommand::getRequestorLocalId)))
			.onErrorResume(PatronNotFoundInHostLmsException.class, this::patronNotFound)
			.onErrorReturn(UnknownHostLmsException.class, unknownHostLms(
				getValue(command, PlacePatronRequestCommand::getRequestorLocalSystemCode)));
	}

	private Mono<Patron> mapToPatron(PlacePatronRequestCommand command) {
		return localPatronService.findLocalPatronAndAgency(
				getValue(command, PlacePatronRequestCommand::getRequestorLocalId),
				getValue(command, PlacePatronRequestCommand::getRequestorLocalSystemCode))
			.map(TupleUtils.function((patron, agency) -> {
				final var homeIdentity = PatronIdentity.builder()
					.homeIdentity(true)
					.resolvedAgency(agency)
					.build();

				return Patron.builder()
					.patronIdentities(List.of(homeIdentity))
					.build();
			}));
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

	private Mono<List<CheckResult>> patronNotFound(PatronNotFoundInHostLmsException error) {
		return Mono.just(List.of(
			failed("PATRON_NOT_FOUND", error.getMessage())
		));
	}

	private Mono<List<CheckResult>> agencyNotFound(
		UnableToResolveAgencyProblem error, String localPatronId) {

		return Mono.just(List.of(
			failed("PATRON_NOT_ASSOCIATED_WITH_AGENCY",
				"Patron \"%s\" with home library code \"%s\" from \"%s\" is not associated with an agency"
					.formatted(localPatronId, error.getHomeLibraryCode(),
						error.getSystemCode()))));
	}

	private static List<CheckResult> unknownHostLms(String localSystemCode) {
		return List.of(failed("UNKNOWN_BORROWING_HOST_LMS",
			"\"%s\" is not a recognised Host LMS".formatted(localSystemCode)));
	}
}

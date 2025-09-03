package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failed;
import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.consumer;
import static reactor.function.TupleUtils.function;

import java.util.List;

import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.core.interaction.LocalPatronService;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.UnableToConvertLocalPatronTypeException;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;
import org.olf.dcb.request.resolution.ManualItemSelection;
import org.olf.dcb.request.resolution.NoBibsForClusterRecordException;
import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;
import org.olf.dcb.request.resolution.ResolutionParameters;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveAgencyProblem;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Slf4j
@Singleton
@Requires(property = "dcb.requests.preflight-checks.resolve-patron-request.enabled", defaultValue = "true", notEquals = "false")
public class ResolvePatronRequestPreflightCheck implements PreflightCheck {
	private final PatronRequestResolutionService patronRequestResolutionService;
	private final LocalPatronService localPatronService;
	private final IntMessageService intMessageService;

	public ResolvePatronRequestPreflightCheck(
		PatronRequestResolutionService patronRequestResolutionService,
		LocalPatronService localPatronService, IntMessageService intMessageService) {

		this.patronRequestResolutionService = patronRequestResolutionService;
		this.localPatronService = localPatronService;
		this.intMessageService = intMessageService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		// This is a workaround due to the current resolution process
		// being too coupled to a patron request
		return Mono.just(command)
			.zipWhen(this::mapToPatron)
			.map(function(ResolvePatronRequestPreflightCheck::mapToParameters))
			.doOnSuccess(patronRequest -> log.debug("Completed mapping to patron request: {}", patronRequest))
			.flatMap(patronRequestResolutionService::resolve)
			.doOnSuccess(resolution -> log.debug("Completed resolution: {}", resolution))
			.map(this::checkResolution)
			// Many of these errors are duplicated from the resolve patron preflight check
			// This is due to both having to resolve the patron before performing the checks
			.onErrorResume(UnableToResolveAgencyProblem.class, error -> agencyNotFound(error,
				getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalId)))
			.onErrorResume(PatronNotFoundInHostLmsException.class, this::patronNotFound)
			.onErrorResume(NoPatronTypeMappingFoundException.class, this::noPatronTypeMappingFound)
			.onErrorResume(UnableToConvertLocalPatronTypeException.class, this::nonNumericPatronType)
			.onErrorResume(CannotFindClusterRecordException.class, this::clusterRecordNotFound)
			.onErrorResume(NoBibsForClusterRecordException.class, this::clusterRecordNotFound)
			.onErrorReturn(UnknownHostLmsException.class, unknownHostLms(
				getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalSystemCode)))
			.defaultIfEmpty(List.of(failedUm("NO_ITEM_SELECTABLE_FOR_REQUEST",
				"Failed due to empty reactive chain",
				intMessageService.getMessage("NO_ITEM_SELECTABLE_FOR_REQUEST"))));
	}

	private Mono<Patron> mapToPatron(PlacePatronRequestCommand command) {
		log.info("mapToPatron {}", command);

		final var localPatronId = getValueOrNull(command,
			PlacePatronRequestCommand::getRequestorLocalId);

		final var borrowingHostLmsCode = getValueOrNull(command,
			PlacePatronRequestCommand::getRequestorLocalSystemCode);

		return localPatronService.findLocalPatronAndAgency(localPatronId, borrowingHostLmsCode)
			.doOnSuccess(consumer((patron, agency) -> log.info("Finished fetching patron: {} and agency: {}", patron, agency)))
			.map(function((patron, agency) -> {
				final var homeIdentity = PatronIdentity.builder()
					.homeIdentity(true)
					.resolvedAgency(agency)
					.build();

				return Patron.builder()
					.patronIdentities(List.of(homeIdentity))
					.build();
			}));
	}

	private static ResolutionParameters mapToParameters(PlacePatronRequestCommand command, Patron patron) {
		log.debug("mapToParameters({}, {})", command, patron);

		return ResolutionParameters.builder()
			.borrowingAgencyCode(getValueOrNull(patron, Patron::determineAgencyCode))
			.borrowingHostLmsCode(getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalSystemCode))
			.bibClusterId(getValueOrNull(command, PlacePatronRequestCommand::getCitation,
				PlacePatronRequestCommand.Citation::getBibClusterId))
			.pickupLocationCode(getValueOrNull(command, PlacePatronRequestCommand::getPickupLocationCode))
			.manualItemSelection(mapManualItemSelection(getValueOrNull(command, PlacePatronRequestCommand::getItem)))
			.build();
	}

	private static ManualItemSelection mapManualItemSelection(PlacePatronRequestCommand.Item item) {
		if (item == null) {
			return null;
		}

		final var id = getValueOrNull(item, PlacePatronRequestCommand.Item::getLocalId);
		final var hostLmsCode = getValueOrNull(item, PlacePatronRequestCommand.Item::getLocalSystemCode);
		final var agencyCode = getValueOrNull(item, PlacePatronRequestCommand.Item::getAgencyCode);

		if (id == null || hostLmsCode == null || agencyCode == null) {
			log.warn("Possibly incomplete manually selected item: {}", item);

			return null;
		}

		return ManualItemSelection.builder()
			.isManuallySelected(true)
			.localItemId(id)
			.hostLmsCode(hostLmsCode)
			.agencyCode(agencyCode)
			.build();
	}

	private List<CheckResult> checkResolution(Resolution resolution) {
		log.debug("checkResolution({})", resolution);

		final var chosenItem = getValueOrNull(resolution, Resolution::getChosenItem);

		if (chosenItem == null) {
			return List.of(failedUm("NO_ITEM_SELECTABLE_FOR_REQUEST",
				"Patron request for cluster record \"%s\" could not be resolved to an item".formatted(resolution.getBibClusterId()),
        intMessageService.getMessage("NO_ITEM_SELECTABLE_FOR_REQUEST"))
			);
		}

		return List.of(passed());
	}

	private Mono<List<CheckResult>> clusterRecordNotFound(CannotFindClusterRecordException error) {
		return Mono.just(List.of(
			failedUm("CLUSTER_RECORD_NOT_FOUND", "Cluster record \"%s\" cannot be found".formatted(getValueOrNull(error, CannotFindClusterRecordException::getClusterRecordId)),
        intMessageService.getMessage("CLUSTER_RECORD_NOT_FOUND")))
		);
	}

	private Mono<List<CheckResult>> clusterRecordNotFound(NoBibsForClusterRecordException error) {
		return Mono.just(List.of(
			failedUm("CLUSTER_RECORD_NOT_FOUND", "Cluster record \"%s\" cannot be found"
				.formatted(getValueOrNull(error, NoBibsForClusterRecordException::getClusterRecordId)),
        intMessageService.getMessage("CLUSTER_RECORD_NOT_FOUND"))
		));
	}

	private Mono<List<CheckResult>> patronNotFound(PatronNotFoundInHostLmsException error) {
		return Mono.just(List.of(
			failed("PATRON_NOT_FOUND", 
      error.getMessage(),
      "A borrower account could not be found using the information provided.",
      intMessageService.getMessage("PATRON_NOT_FOUND"))
		));
	}

	private Mono<List<CheckResult>> agencyNotFound(
		UnableToResolveAgencyProblem error, String localPatronId) {

		return Mono.just(List.of(
			failedUm("PATRON_NOT_ASSOCIATED_WITH_AGENCY",
				"Patron \"%s\" with home library code \"%s\" from \"%s\" is not associated with an agency" .formatted(localPatronId, error.getHomeLibraryCode(), error.getSystemCode()),
        intMessageService.getMessage("PATRON_NOT_ASSOCIATED_WITH_AGENCY")
      )));
	}

	private List<CheckResult> unknownHostLms(String localSystemCode) {
		return List.of(failedUm("UNKNOWN_BORROWING_HOST_LMS",
			"\"%s\" is not a recognised Host LMS".formatted(localSystemCode),
      intMessageService.getMessage("UNKNOWN_BORROWING_HOST_LMS")));
	}

	private Mono<List<CheckResult>> noPatronTypeMappingFound(
		NoPatronTypeMappingFoundException error) {

		return Mono.just(List.of(
			failedUm("PATRON_TYPE_NOT_MAPPED",
				"Local patron type \"%s\" from \"%s\" is not mapped to a DCB canonical patron type".formatted(error.getLocalPatronType(), error.getHostLmsCode()),
        intMessageService.getMessage("PATRON_TYPE_NOT_MAPPED"))
		));
	}

	private Mono<List<CheckResult>> nonNumericPatronType(
		UnableToConvertLocalPatronTypeException error) {

		return Mono.just(List.of(
			failedUm("LOCAL_PATRON_TYPE_IS_NON_NUMERIC",
				"Local patron \"%s\" from \"%s\" has non-numeric patron type \"%s\"".formatted(error.getLocalId(), error.getLocalSystemCode(), error.getLocalPatronTypeCode()),
				intMessageService.getMessage("LOCAL_PATRON_TYPE_IS_NON_NUMERIC")
      )
		));
	}
}

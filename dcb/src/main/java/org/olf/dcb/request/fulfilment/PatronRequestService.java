package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;
import static reactor.function.TupleUtils.function;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.PatronService.PatronId;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand.Requestor;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.storage.AgencyRepository;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class PatronRequestService {
	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestWorkflowService requestWorkflow;
	private final PatronService patronService;
	private final FindOrCreatePatronService findOrCreatePatronService;
	private final PatronRequestPreflightChecksService preflightChecksService;
	private final PatronRequestAuditRepository patronRequestAuditRepository;
	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestAuditService> patronRequestAuditService;
	private final HostLmsService hostLmsService;
	private final BibRepository bibRepository;
	private final AgencyRepository agencyRepository;
	private final LocationService locationService;
	private final IntMessageService intMessageService;

	public PatronRequestService(PatronRequestRepository patronRequestRepository,
		PatronRequestWorkflowService requestWorkflow, PatronService patronService,
		FindOrCreatePatronService findOrCreatePatronService,
		PatronRequestPreflightChecksService preflightChecksService,
		PatronRequestAuditRepository patronRequestAuditRepository, 
		BeanProvider<PatronRequestAuditService> patronRequestAuditService,
															HostLmsService hostLmsService,
															BibRepository bibRepository,
		AgencyRepository agencyRepository, LocationService locationService,
		IntMessageService intMessageService) {

		this.patronRequestRepository = patronRequestRepository;
		this.requestWorkflow = requestWorkflow;
		this.patronService = patronService;
		this.findOrCreatePatronService = findOrCreatePatronService;
		this.preflightChecksService = preflightChecksService;
		this.patronRequestAuditRepository = patronRequestAuditRepository;
		this.patronRequestAuditService = patronRequestAuditService;
		this.hostLmsService = hostLmsService;
		this.bibRepository = bibRepository;
		this.agencyRepository = agencyRepository;
		this.locationService = locationService;
		this.intMessageService = intMessageService;
	}

	public Mono<? extends PatronRequest> placePatronRequest(
		PlacePatronRequestCommand command) {

		log.info("PRS::placePatronRequest({})", command);

		return preflightChecksService.check(withExpedited(command, false))
			.doOnError(PreflightCheckFailedException.class, e -> log.error("Preflight check for request {} failed", command, e))
			.zipWhen(this::findOrCreatePatron)
			.map(function(this::mapToPatronRequest))
			.map(mapManualItemSelectionIfPresent(command))
			.flatMap(this::savePatronRequest)
			.flatMap(savedPatronRequest -> recordRequestPayloadAudit(savedPatronRequest, command))
			.doOnSuccess(requestWorkflow::initiate)
			.doOnError(e -> log.error("Placing request {} failed", command, e));
	}

	/**
	 * Records an audit entry capturing the original request payload sent to DCB.
	 * This provides a source of truth for data validation and debugging purposes.
	 * This method is fail-safe and will not propagate errors to the main workflow.
	 *
	 * @param patronRequest The saved patron request
	 * @param command The original command containing the payload
	 * @return original patronRequest, even if audit fails
	 */
	private Mono<PatronRequest> recordRequestPayloadAudit(PatronRequest patronRequest, PlacePatronRequestCommand command) {
		return Mono.fromCallable(() -> {
				final var auditEntry = PatronRequestAudit.builder()
					.id(UUID.randomUUID())
					.patronRequest(patronRequest)
					.auditDate(Instant.now())
					.fromStatus(patronRequest.getStatus())
					.toStatus(patronRequest.getStatus())
					.briefDescription("DCB request payload captured")
					.auditData(Map.of("originalPayload", command))
					.build();
				return auditEntry;
			})
			.flatMap(audit -> Mono.from(patronRequestAuditRepository.save(audit)))
			.doOnError(error -> log.warn("Failed to record request payload audit for patron request {}: {}",
				patronRequest.getId(), error.getMessage()))
			.onErrorComplete()
			.thenReturn(patronRequest)
			.onErrorReturn(patronRequest);
	}

	public Mono<? extends PatronRequest> placePatronRequestExpeditedCheckout(
		PlacePatronRequestCommand command) {

		log.info("PRS::placePatronRequestExpeditedCheckout({})", command);

		return preflightChecksService.check(withExpedited(command, true))
			.doOnError(PreflightCheckFailedException.class,
				e -> log.error("Preflight check for expedited request {} failed", command, e))
			.zipWhen(this::findOrCreatePatron)
			.map(function(this::mapToPatronRequest))
			.map(mapManualItemSelectionIfPresent(command))
			// Set an expedited flag in the context
			.map(patronRequest -> {
				patronRequest.setIsExpeditedCheckout(true);
				return patronRequest;
			})
			.flatMap(this::savePatronRequest)
			.flatMap(savedPatronRequest -> recordRequestPayloadAudit(savedPatronRequest, command))
			.doOnSuccess(requestWorkflow::initiate)
			.doOnError(e -> log.error("Placing expedited request {} failed", command, e));
	}

	/**
	 * A patron at the lending library's desk with the book in hand. Every refusal is a failed
	 * check the desk can act on, and the ones that need no library system are made before any
	 * library system is called.
	 */
	public Mono<? extends PatronRequest> placeWalkUpRequest(
		WalkUpRequestCommand command) {

		if (command.getPatronAgencyCode().equals(command.getItemAgencyCode())) {
			return refuse("WALK_UP_SAME_LIBRARY",
				"The patron belongs to the library that holds the item, so this is a local loan");
		}

		return hostLmsService.getClientFor(command.getItemHostLmsCode())
			.flatMap(client -> requireItemAgencyOnHostLms(command, client.getHostLms().getId())
				.then(requirePickupAtItemLibrary(command))
				.then(Mono.defer(() -> findWalkUpItem(command, client))));
	}

	private Mono<Void> requireItemAgencyOnHostLms(WalkUpRequestCommand command, UUID itemHostLmsId) {
		return Mono.from(agencyRepository.findOneByCode(command.getItemAgencyCode()))
			.flatMap(agency -> Mono.from(agencyRepository.findHostLmsIdById(agency.getId())))
			.filter(itemHostLmsId::equals)
			.switchIfEmpty(refuse("WALK_UP_ITEM_AGENCY_MISMATCH",
				"Agency %s is not on library system %s".formatted(command.getItemAgencyCode(),
					command.getItemHostLmsCode())))
			.then();
	}

	// The patron collects at the desk they are standing at; any other pickup is not a walk-up
	private Mono<Void> requirePickupAtItemLibrary(WalkUpRequestCommand command) {
		return locationService.findById(command.getPickupLocationCode())
			.flatMap(location -> Mono.justOrEmpty(location.getAgency()))
			.flatMap(agency -> Mono.from(agencyRepository.findById(agency.getId())))
			.filter(agency -> command.getItemAgencyCode().equals(agency.getCode()))
			.switchIfEmpty(refuse("WALK_UP_PICKUP_ELSEWHERE",
				"The pickup location is not at the library that holds the item"))
			.then();
	}

	private Mono<? extends PatronRequest> findWalkUpItem(WalkUpRequestCommand command,
		HostLmsClient client) {

		return client.getItemByBarcode(command.getItemBarcode())
			.switchIfEmpty(refuse("ITEM_NOT_FOUND",
				"No item with that barcode in " + command.getItemHostLmsCode()))
			.flatMap(item -> {
				if (!Objects.equals(item.getStatus(), HostLmsItem.ITEM_AVAILABLE)) {
					return refuse("ITEM_NOT_AVAILABLE",
						"Item %s is not available: its status is %s".formatted(item.getLocalId(), item.getStatus()));
				}

				if (item.getBibId() == null) {
					return refuse("ITEM_NOT_IN_SHARED_INDEX",
						"Item %s has no bibliographic record in %s".formatted(item.getLocalId(),
							command.getItemHostLmsCode()));
				}

				return Mono.from(bibRepository.findBySourceSystemIdAndSourceRecordId(
						client.getHostLms().getId(), item.getBibId()))
					.switchIfEmpty(refuse("ITEM_NOT_IN_SHARED_INDEX",
						"Bibliographic record %s of item %s is not in the shared catalogue".formatted(
							item.getBibId(), item.getLocalId())))
					.flatMap(bibRecord -> placeWalkUp(command, item, bibRecord));
			});
	}

	private Mono<? extends PatronRequest> placeWalkUp(WalkUpRequestCommand command,
		HostLmsItem item, BibRecord bibRecord) {

		final var cluster = bibRecord.getContributesTo();

		if (cluster == null || cluster.getId() == null || Boolean.TRUE.equals(cluster.getIsDeleted())) {
			return refuse("CLUSTER_DELETED",
				"The catalogue entry for item %s has been deleted".formatted(item.getLocalId()));
		}

		final var placeCommand = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localId(command.getPatronLocalId())
				.localSystemCode(command.getPatronHostLmsCode())
				.agencyCode(command.getPatronAgencyCode())
				.build())
			.item(PlacePatronRequestCommand.Item.builder()
				.localId(item.getLocalId())
				.localSystemCode(command.getItemHostLmsCode())
				.agencyCode(command.getItemAgencyCode())
				.build())
			.citation(PlacePatronRequestCommand.Citation.builder()
				.bibClusterId(cluster.getId())
				.build())
			.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder()
				.code(command.getPickupLocationCode())
				.build())
			.description("Walk-up request for item %s at %s".formatted(item.getLocalId(),
				command.getItemHostLmsCode()))
			.build();

		return placePatronRequestExpeditedCheckout(placeCommand);
	}

	// Whether a request is expedited is decided by the entry point it came through; a caller
	// setting the flag on /place would otherwise have preflight and the workflow disagree
	private static PlacePatronRequestCommand withExpedited(PlacePatronRequestCommand command,
		boolean expedited) {

		return command.toBuilder().isExpeditedRequest(expedited).build();
	}

	private <T> Mono<T> refuse(String code, String description) {
		return Mono.error(new PreflightCheckFailedException(List.of(FailedPreflightCheck.fromResult(
			CheckResult.failedUm(code, description, intMessageService.getMessage(code))))));
	}

	private static Function<PatronRequest, PatronRequest> mapManualItemSelectionIfPresent(
		PlacePatronRequestCommand command) {

		if (command.getItem() == null) {
			return patronRequest -> patronRequest.setIsManuallySelectedItem(Boolean.FALSE);
		}
		else if (isFullItemPresent(command.getItem())) {
			return patronRequest -> patronRequest.addManuallySelectedItemDetails(command.getItem());
		}
		// Indicate no item or partial item selected
		return patronRequest -> patronRequest.setIsManuallySelectedItem(Boolean.FALSE);
	}

	private static Boolean isFullItemPresent(PlacePatronRequestCommand.Item item) {
		return item.getLocalId() != null && item.getLocalSystemCode() != null && item.getAgencyCode() != null;
	}

	private Mono<Patron> findOrCreatePatron(PlacePatronRequestCommand command) {
		return findOrCreatePatron(command.getRequestor());
	}

	private Mono<Patron> findOrCreatePatron(Requestor requestor) {
		return findOrCreatePatronService.findOrCreatePatron(requestor.getLocalSystemCode(),
			requestor.getLocalId(), requestor.getHomeLibraryCode());
	}

	private PatronRequest mapToPatronRequest(PlacePatronRequestCommand command, Patron patron) {
		final var id = UUID.randomUUID();

		log.debug("mapToPatronRequest({}, {})", command, patron);

		String rawDescription = command.getDescription();
		String trimmedDescription = rawDescription != null ? rawDescription.substring(0, Math.min(rawDescription.length(), 254)) : null;

		return PatronRequest.builder()
			.id(id)
			.patron(patron)
			.bibClusterId(command.getCitation().getBibClusterId())
			.requestedVolumeDesignation(command.getCitation().getVolumeDesignator())
			.pickupLocationCode(command.getPickupLocationCode())
			.status(SUBMITTED_TO_DCB)
			.description(trimmedDescription)
			.patronHostlmsCode(command.getRequestorLocalSystemCode())
			.requesterNote(command.getRequesterNote())
			.build();
	}

	private Mono<? extends PatronRequest> savePatronRequest(PatronRequest patronRequest) {
		log.debug("savePatronRequest({})", patronRequest);

		// Mono and publishers don't chain very well, so convert to mono
		return Mono.from(patronRequestRepository.save(patronRequest));
	}

	public Mono<PatronRequest> findById(UUID id) {
		return Mono.from(patronRequestRepository.findById(id))
			.zipWhen(this::findPatron, PatronRequestService::addPatron);
	}

	private Mono<Patron> findPatron(PatronRequest patronRequest) {
		return patronService.findById(PatronId.fromPatron(patronRequest.getPatron()));
	}

	private static PatronRequest addPatron(PatronRequest patronRequest, Patron patron) {
		patronRequest.setPatron(patron);

		return patronRequest;
	}

	public Mono<PatronRequest> updatePatronRequest(PatronRequest patronRequest) {
		log.debug("updatePatronRequest({})", patronRequest);

		return Mono.from(patronRequestRepository.update(patronRequest));
	}

	public Mono<List<PatronRequestAudit>> findAllAuditsFor(PatronRequest patronRequest) {
		return Flux.from(patronRequestAuditRepository.findByPatronRequest(patronRequest)).collectList();
	}

	public Mono<UUID> initialiseRollback(@NotNull UUID patronRequestId) {
		log.debug("initialiseRollback({})", patronRequestId);

		return Mono.from(patronRequestRepository.findById(patronRequestId))
			.flatMap( auditRollbackActioned() )
			.filter( applicableRollbackStatuses() )
			.flatMap( rollbackRequest() )

			// For when rollback was skipped or produced an empty
			.switchIfEmpty(Mono.defer(() -> {
				final var auditDataMap = new HashMap<String, Object>();
				auditDataMap.put("reason", "caught empty when attempting to rollback");
				return patronRequestAuditService.get()
					.addAuditEntry(patronRequestId, "Rollback failed.", auditDataMap)
					.map(PatronRequestAudit::getPatronRequest);
			}))

			// Something unexpected happened
			.onErrorResume(error -> {
				final var auditDataMap = new HashMap<String, Object>();
				auditDataMap.put("error", error.toString());
				auditDataMap.put("stacktrace", error.getStackTrace());
				return patronRequestAuditService.get()
					.addAuditEntry(patronRequestId, "Rollback failed.", auditDataMap)
					.map(PatronRequestAudit::getPatronRequest);
			})
			.map(PatronRequest::getId);
	}

	private static Predicate<PatronRequest> applicableRollbackStatuses() {

		// The list of statuses that can be rolled back
		final List<PatronRequest.Status> applicableStatuses = List.of(ERROR);

		return patronRequest -> applicableStatuses.contains(patronRequest.getStatus());
	}

	private Function<PatronRequest, Mono<? extends PatronRequest>> rollbackRequest() {
		return patronRequest -> {

			final var previousStatus = patronRequest.getPreviousStatus();

			patronRequest.setStatus(previousStatus);
			patronRequest.setErrorMessage(null);
			patronRequest.setNextScheduledPoll(Instant.now());

			return updatePatronRequest(patronRequest);
		};
	}

	private Function<PatronRequest, Mono<? extends PatronRequest>> auditRollbackActioned() {
		return patronRequest -> {

			final var auditDataMap = new HashMap<String, Object>();
			auditDataMap.put("rollback-from-status", patronRequest.getStatus());
			auditDataMap.put("rollback-to-status", patronRequest.getPreviousStatus());

			return audit(patronRequest, "Rollback actioned.", auditDataMap)
				.map(PatronRequestAudit::getPatronRequest);
		};
	}

	private Mono<PatronRequestAudit> audit(PatronRequest patronRequest, String message, Map<String, Object> auditData) {
		return patronRequestAuditService.get().addAuditEntry(patronRequest, message, auditData);
	}
}

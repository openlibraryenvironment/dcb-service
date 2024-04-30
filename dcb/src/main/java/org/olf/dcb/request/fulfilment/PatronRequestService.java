package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;
import static reactor.function.TupleUtils.function;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.PatronService.PatronId;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand.Requestor;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.context.annotation.Prototype;
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

	public PatronRequestService(PatronRequestRepository patronRequestRepository,
		PatronRequestWorkflowService requestWorkflow, PatronService patronService,
		FindOrCreatePatronService findOrCreatePatronService,
		PatronRequestPreflightChecksService preflightChecksService,
		PatronRequestAuditRepository patronRequestAuditRepository) {

		this.patronRequestRepository = patronRequestRepository;
		this.requestWorkflow = requestWorkflow;
		this.patronService = patronService;
		this.findOrCreatePatronService = findOrCreatePatronService;
		this.preflightChecksService = preflightChecksService;
		this.patronRequestAuditRepository = patronRequestAuditRepository;
	}

	public Mono<? extends PatronRequest> placePatronRequest(
		PlacePatronRequestCommand command) {

		log.info("PRS::placePatronRequest({})", command);

		return preflightChecksService.check(command)
			.doOnError(PreflightCheckFailedException.class, e -> log.error("Preflight check for request {} failed", command, e))
			.zipWhen(this::findOrCreatePatron)
			.map(function(this::mapToPatronRequest))
			.flatMap(this::savePatronRequest)
			.doOnSuccess(requestWorkflow::initiate)
			.doOnError(e -> log.error("Placing request {} failed", command, e));
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
			.pickupLocationCodeContext(command.getPickupLocationContext())
			.pickupLocationCode(command.getPickupLocationCode())
			.status(SUBMITTED_TO_DCB)
			.description(trimmedDescription)
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

	public Mono<List<PatronRequestAudit>> findAllAuditsFor(PatronRequest patronRequest) {
		return Flux.from(patronRequestAuditRepository.findByPatronRequest(patronRequest)).collectList();
	}
}

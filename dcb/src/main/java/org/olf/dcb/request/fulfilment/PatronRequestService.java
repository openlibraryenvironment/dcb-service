package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;

import java.util.List;
import java.util.UUID;

import io.micronaut.asm.commons.Remapper;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.PatronService.PatronId;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Prototype
public class PatronRequestService {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestService.class);

	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestWorkflowService requestWorkflow;
	private final PatronService patronService;
	private final FindOrCreatePatronService findOrCreatePatronService;
	private final PatronRequestAuditRepository patronRequestAuditRepository;

	public PatronRequestService(PatronRequestRepository patronRequestRepository,
		PatronRequestWorkflowService requestWorkflow, PatronService patronService,
		FindOrCreatePatronService findOrCreatePatronService,
		PatronRequestAuditRepository patronRequestAuditRepository) {
		
		this.patronRequestRepository = patronRequestRepository;
		this.requestWorkflow = requestWorkflow;
		this.patronService = patronService;
		this.findOrCreatePatronService = findOrCreatePatronService;
		this.patronRequestAuditRepository = patronRequestAuditRepository;
	}

	public Mono<? extends PatronRequest> placePatronRequest(
		PlacePatronRequestCommand command) {

		log.debug("placePatronRequest({})", command);

		return Mono.just(command)
			.flatMap(this::findOrCreatePatron)
			.map(patron -> mapToPatronRequest(patron, command))
			.flatMap(this::savePatronRequest)
			.doOnSuccess(requestWorkflow::initiate);
	}

	private Mono<Patron> findOrCreatePatron(PlacePatronRequestCommand command) {
		final var requestor = command.requestor();

		return findOrCreatePatronService.findOrCreatePatron(requestor.localSystemCode(),
			requestor.localId(), requestor.homeLibraryCode());
	}

	private static PatronRequest mapToPatronRequest(Patron patron,
		PlacePatronRequestCommand command) {

		final var id = UUID.randomUUID();

		log.debug(String.format("create pr %s %s %s %s %s", id,
			patron, command.citation().bibClusterId(),
			command.pickupLocation().code(), SUBMITTED_TO_DCB));

		log.debug("Setting request status {}", SUBMITTED_TO_DCB);

		return PatronRequest.builder()
			.id(id)
			.patron(patron)
			.bibClusterId(command.citation().bibClusterId())
			.pickupLocationCode(command.pickupLocation().code())
			.status(SUBMITTED_TO_DCB)
			.description(command.description())
			.build();
	}

	private Mono<? extends PatronRequest> savePatronRequest(
		PatronRequest patronRequest) {

		log.debug("call save on {}", patronRequest);

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

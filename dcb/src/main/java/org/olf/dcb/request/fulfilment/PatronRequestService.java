package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;
import static reactor.function.TupleUtils.function;

import java.util.List;
import java.util.Objects;
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

		return check(command)
			.zipWhen(this::findOrCreatePatron)
			.map(function(this::mapToPatronRequest))
			.flatMap(this::savePatronRequest)
			.doOnSuccess(requestWorkflow::initiate);
	}

	private static Mono<PlacePatronRequestCommand> check(PlacePatronRequestCommand command) {
		final var pickupLocationCode = command.getPickupLocation().getCode();

		if (Objects.equals(pickupLocationCode, "unknown-pickup-location")) {
			throw CheckFailedException.builder()
				.failedChecks(List.of(Check.builder()
					.failureDescription("\"" + command.getPickupLocation().getCode() + "\" is not a recognised pickup location code")
					.build()))
				.build();
		}

		return Mono.just(command);
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

		log.debug(String.format("create pr %s %s %s %s %s", id,
			patron, command.getCitation().getBibClusterId(),
			command.getPickupLocation().getCode(), SUBMITTED_TO_DCB));

		log.debug("Setting request status {}", SUBMITTED_TO_DCB);

		return PatronRequest.builder()
			.id(id)
			.patron(patron)
			.bibClusterId(command.getCitation().getBibClusterId())
			.pickupLocationCodeContext(command.getPickupLocation().getContext())
			.pickupLocationCode(command.getPickupLocation().getCode())
			.status(SUBMITTED_TO_DCB)
			.description(command.getDescription())
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

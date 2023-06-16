package org.olf.reshare.dcb.request.fulfilment;

import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.util.UUID;

import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.request.fulfilment.PatronService.PatronId;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class PatronRequestService {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestService.class);

	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestWorkflow requestWorkflow;
	private final PatronService patronService;
	private final FindOrCreatePatronService findOrCreatePatronService;

	public PatronRequestService(PatronRequestRepository patronRequestRepository,
		PatronRequestWorkflow requestWorkflow, PatronService patronService,
		FindOrCreatePatronService findOrCreatePatronService) {
		
		this.patronRequestRepository = patronRequestRepository;
		this.requestWorkflow = requestWorkflow;
		this.patronService = patronService;
		this.findOrCreatePatronService = findOrCreatePatronService;
	}

	public Mono<? extends PatronRequest> placePatronRequest(
		PlacePatronRequestCommand command) {

		log.debug("placePatronRequest({})", command);

		return Mono.just(command)
			.flatMap(this::findOrCreatePatron)
			.map(patron -> mapToPatronRequest(patron, command))
			.flatMap(req -> addRequestingPatronIdentity(req, command))
			.flatMap(this::savePatronRequest)
			.doOnSuccess(requestWorkflow::initiate);
	}

	private Mono<Patron> findOrCreatePatron(PlacePatronRequestCommand command) {
		final var requestor = command.requestor();

		return findOrCreatePatronService.findOrCreatePatron(requestor.localSystemCode(),
			requestor.localId(), requestor.homeLibraryCode());
	}

        // Retrieve the identity the patron used to place this request - this is useful to have at the patronRequest level later on
        // as we often want to display patron info in close proximity to the request details itself
        private static Mono<PatronRequest> addRequestingPatronIdentity(PatronRequest req, PlacePatronRequestCommand command) {
          return Mono.just(req);
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
			.statusCode(SUBMITTED_TO_DCB)
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
}

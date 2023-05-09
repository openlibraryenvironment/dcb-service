package org.olf.reshare.dcb.request.fulfilment;

import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.util.UUID;

import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestService {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestService.class);
	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestWorkflow requestWorkflow;
	private final PatronService patronService;

	public PatronRequestService(PatronRequestRepository patronRequestRepository,
		PatronRequestWorkflow requestWorkflow, PatronService patronService) {
		this.patronRequestRepository = patronRequestRepository;
		this.requestWorkflow = requestWorkflow;
		this.patronService = patronService;
	}

	public Mono<? extends PatronRequest> placePatronRequest(
		PlacePatronRequestCommand command) {

		log.debug("placePatronRequest({})", command);

		return Mono.just(command)
			.flatMap(patronService::getOrCreatePatronForRequestor)
			.map(patron -> mapToPatronRequest(patron, command))
			.flatMap(this::savePatronRequest)
			.doOnSuccess(requestWorkflow::initiate);
	}

	private static PatronRequest mapToPatronRequest(Patron patron,
		PlacePatronRequestCommand command) {

		final var uuid = UUID.randomUUID();
		log.debug(String.format("create pr %s %s %s %s %s",uuid,
			patron,
			command.citation().bibClusterId(),
			command.pickupLocation().code(),
			SUBMITTED_TO_DCB));

		log.debug("Setting request status {}", SUBMITTED_TO_DCB);
		return new PatronRequest(uuid, null, null,
			patron, command.citation().bibClusterId(),
			command.pickupLocation().code(),
			SUBMITTED_TO_DCB, null);
	}

	private Mono<? extends PatronRequest> savePatronRequest(
		PatronRequest patronRequest) {

		log.debug("call save on {}", patronRequest);

		// Mono and publishers don't chain very well, so convert to mono
		return Mono.from(patronRequestRepository.save(patronRequest));
	}

	public Mono<PatronRequest> findById(UUID id) {
		return Mono.from(patronRequestRepository.findById(id))
			.flatMap(patronService::addPatronIdentitiesAndHostLms);
	}
}

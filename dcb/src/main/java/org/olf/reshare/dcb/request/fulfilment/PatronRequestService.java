package org.olf.reshare.dcb.request.fulfilment;

import java.util.UUID;

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

	public PatronRequestService(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	public Mono<PatronRequest> placePatronRequest(
		PlacePatronRequestCommand command) {

		log.debug(String.format("placePatronRequest(%s)", command));

		return Mono.just(command)
			.map(PatronRequestService::mapToPatronRequest)
			.flatMap(this::savePatronRequest);
	}

	private static PatronRequest mapToPatronRequest(
		PlacePatronRequestCommand command) {

		final var uuid = UUID.randomUUID();

		log.debug(String.format("create pr %s %s %s %s %s",uuid,
			command.requestor().identifier(),
			command.requestor().agency().code(),
			command.citation().bibClusterId(),
			command.pickupLocation().code()));

		return new PatronRequest(uuid,
			command.requestor().identifier(),
			command.requestor().agency().code(),
			command.citation().bibClusterId(),
			command.pickupLocation().code());
	}

	private Mono<? extends PatronRequest> savePatronRequest(
		PatronRequest patronRequest) {

		log.debug("call save on {}", patronRequest);

		// Mono and publishers don't chain very well, so convert to mono
		return Mono.from(patronRequestRepository.save(patronRequest));
	}

	public Mono<PatronRequest> findById(UUID id) {
		return Mono.from(patronRequestRepository.findById(id));
	}
}

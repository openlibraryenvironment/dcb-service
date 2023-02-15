package org.olf.reshare.dcb.request.fulfilment;

import java.util.UUID;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestService {
	@Inject
	PatronRequestRepository patronRequestRepository;

	public PatronRequestService(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	public static final Logger log = LoggerFactory.getLogger(PatronRequestService.class);

	public Mono<PlacePatronRequestCommand> placePatronRequest(
		Mono<PlacePatronRequestCommand> command) {

		log.debug(String.format("placePatronRequest(%s)", command));

		return command
			.map(PatronRequestService::mapToPatronRequest)
			.flatMap(this::savePatronRequest)
			.map(PatronRequestService::mapToResult);
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

	private static PlacePatronRequestCommand mapToResult(PatronRequest pr) {
		// This is strange because we use a command for both in an out representations
		final var result = new PlacePatronRequestCommand(pr.getId(),
			new PlacePatronRequestCommand.Citation(pr.getBibClusterId()),
			new PlacePatronRequestCommand.PickupLocation(pr.getPickupLocationCode()),
			new PlacePatronRequestCommand.Requestor(pr.getPatronId(),
				new PlacePatronRequestCommand.Agency(pr.getPatronAgencyCode())));

		log.debug("returning {}", result);

		return result;
	}

	public Mono<PlacePatronRequestCommand> getPatronRequestWithId(UUID id) {
		// This is strange because we use a command for both in an out representations
		return Mono.from(patronRequestRepository.findById(id))
			.map(pr -> new PlacePatronRequestCommand(pr.getId(),
				new PlacePatronRequestCommand.Citation(pr.getBibClusterId()),
				new PlacePatronRequestCommand.PickupLocation(pr.getPatronAgencyCode()),
				new PlacePatronRequestCommand.Requestor(pr.getPatronId(),
					new PlacePatronRequestCommand.Agency(pr.getPatronAgencyCode()))));
	}
}

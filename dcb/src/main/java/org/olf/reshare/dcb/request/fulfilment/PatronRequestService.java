package org.olf.reshare.dcb.request.fulfilment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestService {
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestService supplierRequestService;

	public PatronRequestService(PatronRequestRepository patronRequestRepository, SupplierRequestService supplierRequestService) {
		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestService = supplierRequestService;
	}

	public static final Logger log = LoggerFactory.getLogger(PatronRequestService.class);

	public Mono<PatronRequestView> placePatronRequest(
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

	private static PatronRequestView mapToResult(PatronRequest patronRequest) {
		// This is strange because we use a command for both in an out representations
		final var result = new PatronRequestView(patronRequest.getId(),
			new PatronRequestView.Citation(patronRequest.getBibClusterId()),
			new PatronRequestView.PickupLocation(patronRequest.getPickupLocationCode()),
			new PatronRequestView.Requestor(patronRequest.getPatronId(),
				new PatronRequestView.Agency(patronRequest.getPatronAgencyCode())),
			new ArrayList<>());

		log.debug("returning {}", result);

		return result;
	}

	public Mono<PatronRequestView> findPatronRequestById(UUID id) {
		log.debug("findPatronRequestById({})", id);
		return Mono.from( patronRequestRepository.findById(id) )
			.flatMap(supplierRequestService::findSupplierRequest);
	}
}

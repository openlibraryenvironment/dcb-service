package org.olf.reshare.dcb.request.fulfilment;

import java.util.UUID;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.processing.PatronRequestRecord;
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


	public Mono<PatronRequestRecord> savePatronRequest(
		Mono<PatronRequestRecord> patronRequestRecordMono) {

		log.debug(String.format("savePatronRequest(%s)", patronRequestRecordMono));

		return patronRequestRecordMono
			.map(PatronRequestService::mapToPatronRequest)
			.flatMap(this::savePatronRequest)
			.map(PatronRequestService::mapToResult);
	}

	private static PatronRequest mapToPatronRequest(PatronRequestRecord request) {
		final var uuid = UUID.randomUUID();

		log.debug(String.format("create pr %s %s %s %s %s",uuid,
			request.requestor().identifier(),
			request.requestor().agency().code(),
			request.citation().bibClusterId(),
			request.pickupLocation().code()));

		return new PatronRequest(uuid,
			request.requestor().identifier(),
			request.requestor().agency().code(),
			request.citation().bibClusterId(),
			request.pickupLocation().code());
	}

	private Mono<? extends PatronRequest> savePatronRequest(
		PatronRequest patronRequest) {

		log.debug("call save on {}", patronRequest);

		// Mono and publishers don't chain very well, so convert to mono
		return Mono.from(patronRequestRepository.save(patronRequest));
	}

	private static PatronRequestRecord mapToResult(PatronRequest pr) {
		final var result = new PatronRequestRecord(pr.getId(),
			new PatronRequestRecord.Citation(pr.getBibClusterId()),
			new PatronRequestRecord.PickupLocation(pr.getPickupLocationCode()),
			new PatronRequestRecord.Requestor(pr.getPatronId(),
				new PatronRequestRecord.Agency(pr.getPatronAgencyCode())));

		log.debug("returning {}", result);

		return result;
	}

	public Mono<PatronRequestRecord> getPatronRequestWithId(UUID id) {
		return Mono.from(patronRequestRepository.findById(id))
			.map(pr -> new PatronRequestRecord(pr.getId(),
				new PatronRequestRecord.Citation(pr.getBibClusterId()),
				new PatronRequestRecord.PickupLocation(pr.getPatronAgencyCode()),
				new PatronRequestRecord.Requestor(pr.getPatronId(),
					new PatronRequestRecord.Agency(pr.getPatronAgencyCode()))));
	}
}

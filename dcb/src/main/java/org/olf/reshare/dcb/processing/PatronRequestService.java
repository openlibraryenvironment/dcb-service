package org.olf.reshare.dcb.processing;

import jakarta.inject.Singleton;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Singleton
public class PatronRequestService {

	private final PatronRequestRepository patronRequestRepository;
	public PatronRequestService(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	public Mono<PatronRequestRecord> savePatronRequest(Mono<PatronRequestRecord> patronRequestRecordMono) {

		// create new id
		UUID uuid = UUID.randomUUID();

		// get model representation
		PatronRequest patronRequest = new PatronRequest();

		return patronRequestRecordMono
			.doOnNext(pr -> {
				patronRequest.setId(uuid);
				patronRequest.setPatronId(pr.requestor().identifier());
				patronRequest.setPatronAgencyCode(pr.requestor().agency().code());
				patronRequest.setBibClusterId(pr.citation().bibClusterId());
				patronRequest.setPickupLocationCode(pr.pickupLocation().code());
				patronRequestRepository.save(patronRequest);
			})
			.map(pr -> new PatronRequestRecord(uuid, pr.citation(), pr.pickupLocation(), pr.requestor()));
	}

	public Mono<PatronRequestRecord> getPatronRequestWithId(UUID id) {
		return Mono.from(patronRequestRepository.findById(id))
			.map(pr -> new PatronRequestRecord(pr.getId(),
				new PatronRequestRecord.Citation(pr.getBibClusterId()),
				new PatronRequestRecord.PickupLocation(pr.getPatronAgencyCode()),
				new PatronRequestRecord.Requestor(pr.getPatronId(), new PatronRequestRecord.Agency(pr.getPatronAgencyCode()))));
	}


}

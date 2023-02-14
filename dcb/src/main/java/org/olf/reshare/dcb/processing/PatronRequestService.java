package org.olf.reshare.dcb.processing;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.olf.reshare.dcb.core.api.PatronRequestController;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import java.util.UUID;
import javax.transaction.Transactional;

@Singleton
public class PatronRequestService {

	@Inject
	PatronRequestRepository patronRequestRepository;

	public PatronRequestService(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	public static final Logger log = LoggerFactory.getLogger(PatronRequestService.class);

        @Transactional
	public Mono<PatronRequestRecord> savePatronRequest(Mono<PatronRequestRecord> patronRequestRecordMono) {

                log.debug(String.format("savePatronRequest(%s)",patronRequestRecordMono));

		// create new id
		UUID uuid = UUID.randomUUID();

		// get model representation
		PatronRequest patronRequest = new PatronRequest();

		return patronRequestRecordMono
			.map(pr -> new PatronRequestRecord(uuid, pr.citation(), pr.pickupLocation(), pr.requestor()))
			.map(pr -> {
                                log.debug(String.format("create pr %s %s %s %s %s",uuid,
                                                                                   pr.requestor().identifier(),
                                                                                   pr.requestor().agency().code(),
                                                                                   pr.citation().bibClusterId(),
                                                                                   pr.pickupLocation().code()));
				patronRequest.setId(uuid);
				patronRequest.setPatronId(pr.requestor().identifier());
				patronRequest.setPatronAgencyCode(pr.requestor().agency().code());
				patronRequest.setBibClusterId(pr.citation().bibClusterId());
				patronRequest.setPickupLocationCode(pr.pickupLocation().code());
                                log.debug(String.format("Execute save %s",patronRequest));
				patronRequestRepository.save(patronRequest);
				return pr;
			});
	}

	public Mono<PatronRequestRecord> getPatronRequestWithId(UUID id) {
		return Mono.from(patronRequestRepository.findById(id))
			.map(pr -> new PatronRequestRecord(pr.getId(),
				new PatronRequestRecord.Citation(pr.getBibClusterId()),
				new PatronRequestRecord.PickupLocation(pr.getPatronAgencyCode()),
				new PatronRequestRecord.Requestor(pr.getPatronId(), new PatronRequestRecord.Agency(pr.getPatronAgencyCode()))));
	}
}

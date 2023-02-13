package org.olf.reshare.dcb.processing;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Singleton;
import org.olf.reshare.dcb.core.api.datavalidation.*;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.storage.BibRepository;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Singleton
public class PatronRequestService {

        public static final Logger log = LoggerFactory.getLogger(PatronRequestService.class);

	private final PatronRequestRepository patronRequestRepository;
	public PatronRequestService(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	public Mono<PatronRequestCommand> savePatronRequest(Mono<PatronRequestCommand> patronRequestCommand) {

                log.debug("savePatronRequest({})", patronRequestCommand);

		// create new id
		UUID uuid = UUID.randomUUID();

		// get model representation
		PatronRequest patronRequest = new PatronRequest();

		patronRequestCommand
			.doOnNext(pr -> {
				pr.setId(uuid);
				patronRequest.setId(uuid);
				patronRequest.setPatronId(pr.getRequestor().getIdentifiier());
				patronRequest.setPatronAgencyCode(pr.getRequestor().getAgency().getCode());
				patronRequest.setBibClusterId(pr.getCitation().getBibClusterId());
				patronRequest.setPickupLocationCode(pr.getPickupLocation().getCode());
			})
			.map(p -> {
                                log.debug("call save on {}", patronRequest);
				patronRequestRepository.save(patronRequest);
				return p;
			});

                log.debug("returning {}", patronRequestCommand);
		return patronRequestCommand;
	}

	public Mono<PatronRequestCommand> getPatronRequestWithId(UUID id) {

		// Bean to return
		PatronRequestCommand patronRequestCommand = new PatronRequestCommand();
		CitationCommand citationCommand = new CitationCommand();
		RequestorCommand requestorCommand = new RequestorCommand();
		PickupLocationCommand pickupLocationCommand = new PickupLocationCommand();
		AgencyCommand agencyCommand = new AgencyCommand();

		return Mono.from(patronRequestRepository.findById(id))
			.map(p -> {
				// convert model to bean
				patronRequestCommand.setId(p.getId());

				citationCommand.setBibClusterId(p.getBibClusterId());
				patronRequestCommand.setCitation(citationCommand);

				agencyCommand.setCode(p.getPatronAgencyCode());
				requestorCommand.setIdentifiier(p.getPatronId());
				requestorCommand.setAgency(agencyCommand);
				patronRequestCommand.setRequestor(requestorCommand);

				pickupLocationCommand.setCode(p.getPickupLocationCode());
				patronRequestCommand.setPickupLocation(pickupLocationCommand);

				return patronRequestCommand;
			});
	}

}

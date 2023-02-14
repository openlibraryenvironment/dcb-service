package org.olf.reshare.dcb.request.fulfilment;

import java.util.UUID;

import org.olf.reshare.dcb.core.api.datavalidation.AgencyCommand;
import org.olf.reshare.dcb.core.api.datavalidation.CitationCommand;
import org.olf.reshare.dcb.core.api.datavalidation.PatronRequestCommand;
import org.olf.reshare.dcb.core.api.datavalidation.PickupLocationCommand;
import org.olf.reshare.dcb.core.api.datavalidation.RequestorCommand;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestService {

        public static final Logger log = LoggerFactory.getLogger(PatronRequestService.class);

	private final PatronRequestRepository patronRequestRepository;
	public PatronRequestService(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	public Mono<PatronRequestCommand> savePatronRequest(
		Mono<PatronRequestCommand> patronRequestCommand) {

		log.debug("savePatronRequest({})", patronRequestCommand);

		return patronRequestCommand
			.map(PatronRequestService::mapToPatronRequest)
			.flatMap(this::savePatronRequest)
			.map(PatronRequestService::mapToResult);
	}

	private static PatronRequest mapToPatronRequest(PatronRequestCommand command) {
		final var patronRequest = new PatronRequest();

		patronRequest.setId(UUID.randomUUID());
		patronRequest.setPatronId(command.getRequestor().getIdentifiier());
		patronRequest.setPatronAgencyCode(command.getRequestor().getAgency().getCode());
		patronRequest.setBibClusterId(command.getCitation().getBibClusterId());
		patronRequest.setPickupLocationCode(command.getPickupLocation().getCode());

		return patronRequest;
	}

	private Mono<? extends PatronRequest> savePatronRequest(
		PatronRequest patronRequest) {

		log.debug("call save on {}", patronRequest);

		// Mono and publishers don't chain very well, so convert to mono
		return Mono.from(patronRequestRepository.save(patronRequest));
	}

	private static PatronRequestCommand mapToResult(PatronRequest patronRequest) {
		// This is a strange name because we are using a command as a return value
		final var resultCommand = new PatronRequestCommand(patronRequest.getId(),
			new CitationCommand(patronRequest.getBibClusterId()),
			new RequestorCommand(patronRequest.getPatronId(),
				new AgencyCommand(patronRequest.getPatronAgencyCode())),
			new PickupLocationCommand(patronRequest.getPickupLocationCode()));

		log.debug("returning {}", resultCommand);
		return resultCommand;
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

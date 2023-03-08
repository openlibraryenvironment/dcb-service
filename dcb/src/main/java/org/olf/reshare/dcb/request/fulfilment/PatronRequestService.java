package org.olf.reshare.dcb.request.fulfilment;

import java.util.Objects;
import java.util.UUID;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

@Singleton
public class PatronRequestService {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestService.class);

	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;

	private final PatronRequestResolutionService patronRequestResolutionService;

	public PatronRequestService(PatronRequestRepository patronRequestRepository, SupplierRequestRepository supplierRequestRepository,
		PatronRequestResolutionService patronRequestResolutionService) {
		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.patronRequestResolutionService = patronRequestResolutionService;
	}

	public Mono<PatronRequest> placePatronRequest(
		PlacePatronRequestCommand command) {

		log.debug("placePatronRequest({})", command);

		return Mono.just(command)
			.map(PatronRequestService::mapToPatronRequest)
			.flatMap(this::savePatronRequest)
			.flatMap(patronRequestResolutionService::resolvePatronRequest)
			.flatMap(this::updatePatronRequest)
			.flatMap(this::saveSupplierRequest);
	}

	private Mono<SupplierRequest> updatePatronRequest(SupplierRequest supplierRequest) {
		log.debug("updatePatronRequest {}", supplierRequest.getPatronRequest().getStatusCode());
		return Mono.from(patronRequestRepository.update(supplierRequest.getPatronRequest()))
			.then(Mono.just(supplierRequest));
	}

	private Mono<PatronRequest> saveSupplierRequest(SupplierRequest supplierRequest) {
		log.debug("saveSupplierRequest {}", supplierRequest);
		return Mono.from(supplierRequestRepository.save(supplierRequest))
			.then(Mono.just(Objects.requireNonNull(supplierRequest.getPatronRequest())));
	}

	private static PatronRequest mapToPatronRequest(
		PlacePatronRequestCommand command) {

		final var uuid = UUID.randomUUID();
		log.debug(String.format("create pr %s %s %s %s %s %s",uuid,
			command.requestor().identifier(),
			command.requestor().agency().code(),
			command.citation().bibClusterId(),
			command.pickupLocation().code(),
			SUBMITTED_TO_DCB));

		log.debug("Setting request status {}", SUBMITTED_TO_DCB);
		return new PatronRequest(uuid,
			command.requestor().identifier(),
			command.requestor().agency().code(),
			command.citation().bibClusterId(),
			command.pickupLocation().code(),
			SUBMITTED_TO_DCB);
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

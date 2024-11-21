package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.model.*;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.storage.InactiveSupplierRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PENDING;

@Slf4j
@Singleton
public class SupplierRequestService {
	private final SupplierRequestRepository supplierRequestRepository;
	private final InactiveSupplierRequestRepository inactiveSupplierRequestRepository;
	private final AgencyService agencyService;

	public SupplierRequestService(SupplierRequestRepository supplierRequestRepository,
		InactiveSupplierRequestRepository inactiveSupplierRequestRepository, AgencyService agencyService) {

		this.supplierRequestRepository = supplierRequestRepository;
		this.inactiveSupplierRequestRepository = inactiveSupplierRequestRepository;
		this.agencyService = agencyService;
	}

	public Mono<List<SupplierRequest>> findAllSupplierRequestsWithDataAgencyFor(PatronRequest patronRequest) {
		return Flux.from(supplierRequestRepository.findAllByPatronRequestAndIsActive(patronRequest, true))
			.flatMap(this::includeResolvedAgency)
			.collectList();
	}

	public Mono<List<SupplierRequest>> findAllSupplierRequestsFor(PatronRequest patronRequest) {
		return Flux.from(supplierRequestRepository.findAllByPatronRequestAndIsActive(patronRequest, true))
			.collectList();
	}

	private Mono<SupplierRequest> includeResolvedAgency(SupplierRequest supplierRequest) {

		final var resolvedAgencyUUID = extractResolvedAgencyUUID(supplierRequest);

		if (resolvedAgencyUUID == null) {
			log.error("SupplierRequest {} has no resolved agency", supplierRequest.getId());
			return Mono.just(supplierRequest);
		}

		return agencyService.findById(extractResolvedAgencyUUID(supplierRequest))
			.map(supplierRequest::setResolvedAgency);
	}

	// ToDo: This is not safe.. later on we will have multiple supplier requests for a patron request this method
	// is probably looking for the active supplier request
	public Mono<SupplierRequest> findSupplierRequestFor(PatronRequest patronRequest) {
		return findAllSupplierRequestsFor(patronRequest)
			.mapNotNull(supplierRequests ->
				supplierRequests.stream()
					.findFirst()
					.orElse(null));

			// There may be no supplier request yet for this patron request
			// .switchIfEmpty(Mono.error(() -> new RuntimeException("No SupplierRequests found for PatronRequest")));
	}

	public static SupplierRequest findFirstSupplierRequestOrNull(List<SupplierRequest> supplierRequests) {
		return supplierRequests.stream()
			.findFirst()
			.orElse(null);
	}

	public Mono<? extends SupplierRequest> saveSupplierRequest(SupplierRequest supplierRequest) {
		log.debug("saveSupplierRequest({})", supplierRequest);

		return Mono.from(supplierRequestRepository.save(supplierRequest));
	}

	public Mono<SupplierRequest> updateSupplierRequest(SupplierRequest supplierRequest) {
		log.debug("updateSupplierRequest({})", supplierRequest);

		return Mono.from(supplierRequestRepository.update(supplierRequest));
	}

	public static SupplierRequest mapToSupplierRequest(Item item, PatronRequest patronRequest) {
		log.debug("mapToSupplierRequest({}, {})", item, patronRequest);

		final var supplierRequestId = randomUUID();

		log.debug("create SupplierRequest: {}, {}, {}", supplierRequestId, item, item.getHostLmsCode());

		return SupplierRequest.builder()
			.id(supplierRequestId)
			.patronRequest(patronRequest)
			.localItemId(item.getLocalId())
			.localBibId(item.getLocalBibId())
			.localItemBarcode(item.getBarcode())
			.localItemLocationCode(item.getLocation().getCode())
			.localItemType(item.getLocalItemType())
			.canonicalItemType(item.getCanonicalItemType())
			.hostLmsCode(item.getHostLmsCode())
			.localAgency(item.getAgencyCode())
			.statusCode(PENDING)
			.isActive(true)
			.resolvedAgency(item.getAgency())
			.build();
	}

	// Utility method to extract resolved agency uuid
	public static UUID extractResolvedAgencyUUID(SupplierRequest supplierRequest) {
		return Optional.ofNullable(supplierRequest)
			.map(SupplierRequest::getResolvedAgency)
			.map(DataAgency::getId)
			.orElse(null);
	}

	public Mono<InactiveSupplierRequest> saveInactiveSupplierRequest(SupplierRequest supplierRequest) {

		final var inactiveSupplierRequest = buildInactiveSupplierRequestFor(supplierRequest);
		log.debug("saveInactiveSupplierRequest({})", inactiveSupplierRequest);

		return Mono.from(supplierRequestRepository.delete(supplierRequest.getId()))
			.then(Mono.from(inactiveSupplierRequestRepository.save(inactiveSupplierRequest)))
			.cast(InactiveSupplierRequest.class)
			.doOnSuccess(success -> log.info("Supplier request {} saved as inactive supplier request", success))
			.doOnError(error -> log.error("Error saving supplier request {} as inactive supplier request", supplierRequest.getId(), error));
	}

	private static InactiveSupplierRequest buildInactiveSupplierRequestFor(SupplierRequest supplierRequest) {
		return InactiveSupplierRequest.builder()
			.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "InactiveSupplierRequest:" + supplierRequest.getLocalId()))
			.patronRequest(supplierRequest.getPatronRequest())
			.localItemId(supplierRequest.getLocalItemId())
			.localBibId(supplierRequest.getLocalBibId())
			.localItemBarcode(supplierRequest.getLocalItemBarcode())
			.localItemLocationCode(supplierRequest.getLocalItemLocationCode())
			.localItemType(supplierRequest.getLocalItemType())
			.canonicalItemType(supplierRequest.getCanonicalItemType())
			.hostLmsCode(supplierRequest.getHostLmsCode())
			.localAgency(supplierRequest.getLocalAgency())
			.statusCode(supplierRequest.getStatusCode())
			.resolvedAgency(supplierRequest.getResolvedAgency())
			.build();
	}
}

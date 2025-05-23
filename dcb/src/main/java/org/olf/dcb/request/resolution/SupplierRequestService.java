package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PENDING;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.InactiveSupplierRequest;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.storage.InactiveSupplierRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.utils.CollectionUtils;

import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

@Slf4j
@Singleton
@AllArgsConstructor
public class SupplierRequestService {
	private final SupplierRequestRepository supplierRequestRepository;
	private final InactiveSupplierRequestRepository inactiveSupplierRequestRepository;
	private final AgencyService agencyService;

	public Mono<List<SupplierRequest>> findAllActiveSupplierRequestsFor(PatronRequest patronRequest) {
		log.debug("findAllActiveSupplierRequestsFor({})", patronRequest);

		return Flux.from(supplierRequestRepository.findAllByPatronRequestAndIsActive(patronRequest, true))
			.doOnNext(supplierRequest -> log.debug("found supplier request: {}", supplierRequest))
			.flatMap(this::includeResolvedAgency)
			.collectList();
	}

	// ToDo: This is not safe.. later on we will have multiple supplier requests for a patron request this method
	// is probably looking for the active supplier request
	public Mono<SupplierRequest> findActiveSupplierRequestFor(PatronRequest patronRequest) {
		return findAllActiveSupplierRequestsFor(patronRequest)
			.mapNotNull(CollectionUtils::firstValueOrNull);

			// There may be no supplier request yet for this patron request
			// .switchIfEmpty(Mono.error(() -> new RuntimeException("No SupplierRequests found for PatronRequest")));
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
			.localHoldingId(getValueOrNull(item, Item::getLocalHoldingId))
			.build();
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

	public Flux<DataAgency> findAllSupplyingAgencies(PatronRequest patronRequest) {
		return findAgenciesFromSupplierRequests(patronRequest)
			.concatWith(findAgenciesFromInactiveSupplierRequests(patronRequest));
	}

	private Flux<DataAgency> findAgenciesFromSupplierRequests(PatronRequest patronRequest) {
		return Flux.from(supplierRequestRepository.findAllByPatronRequest(patronRequest))
			.flatMap(this::includeResolvedAgency)
			.mapNotNull(SupplierRequest::getResolvedAgency);
	}

	private Flux<DataAgency> findAgenciesFromInactiveSupplierRequests(PatronRequest patronRequest) {
		return Flux.from(inactiveSupplierRequestRepository.findAllByPatronRequest(patronRequest))
			.flatMap(this::includeResolvedAgency)
			.mapNotNull(InactiveSupplierRequest::getResolvedAgency);
	}

	private Mono<SupplierRequest> includeResolvedAgency(SupplierRequest supplierRequest) {
		log.debug("includeResolvedAgency({})", supplierRequest);

		return findResolvedAgencyForSupplierRequest(supplierRequest)
			.map(supplierRequest::setResolvedAgency)
			.defaultIfEmpty(supplierRequest);
	}

	private Mono<DataAgency> findResolvedAgencyForSupplierRequest(SupplierRequest supplierRequest) {
		final var resolvedAgencyId = getValueOrNull(supplierRequest, SupplierRequest::getResolvedAgencyId);

		if (resolvedAgencyId == null) {
			log.warn("SupplierRequest {} has no resolved agency", supplierRequest.getId());

			return Mono.empty();
		}

		return agencyService.findById(resolvedAgencyId);
	}

	private Mono<InactiveSupplierRequest> includeResolvedAgency(InactiveSupplierRequest supplierRequest) {
		log.debug("includeResolvedAgency({})", supplierRequest);

		return findResolvedAgencyForSupplierRequest(supplierRequest)
			.map(supplierRequest::setResolvedAgency)
			.defaultIfEmpty(supplierRequest);
	}

	private Mono<DataAgency> findResolvedAgencyForSupplierRequest(InactiveSupplierRequest supplierRequest) {
		final var resolvedAgencyId = getValueOrNull(supplierRequest, InactiveSupplierRequest::getResolvedAgencyId);

		if (resolvedAgencyId == null) {
			log.warn("SupplierRequest {} has no resolved agency", supplierRequest.getId());

			return Mono.empty();
		}

		return agencyService.findById(resolvedAgencyId);
	}
}

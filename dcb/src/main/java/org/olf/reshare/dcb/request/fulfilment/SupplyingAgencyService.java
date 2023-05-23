package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Prototype
public class SupplyingAgencyService {
	private static final Logger log = LoggerFactory.getLogger(SupplyingAgencyService.class);

	private final HostLmsService hostLmsService;
	private final SupplierRequestService supplierRequestService;
	private final PatronService patronService;
	private final PatronTypeService patronTypeService;

	public SupplyingAgencyService(
		HostLmsService hostLmsService, SupplierRequestService supplierRequestService,
		PatronService patronService, PatronTypeService patronTypeService) {
		this.hostLmsService = hostLmsService;
		this.supplierRequestService = supplierRequestService;
		this.patronService = patronService;
		this.patronTypeService = patronTypeService;
	}

	public Mono<PatronRequest> placePatronRequestAtSupplyingAgency(PatronRequest patronRequest) {
		log.debug("placePatronRequestAtSupplyingAgency {}", patronRequest.getId());

		return getSupplierRequestTuple(patronRequest)
			.flatMap(tuple -> checkAndCreatePatronAtSupplier(tuple.getT1(), tuple.getT2())
				.flatMap(supplierIdentity -> placeRequestAtSupplier(tuple.getT1(), tuple.getT2(), supplierIdentity))
				.flatMap(supplierRequestService::updateSupplierRequest)
				.map(placed -> tuple.getT1().placedAtSupplyingAgency())
			);
	}

	public Mono<PatronIdentity> checkAndCreatePatronAtSupplier(PatronRequest patronRequest,
		SupplierRequest supplierRequest) {
		return checkIfPatronExistsAtSupplier(patronRequest, supplierRequest)
			.switchIfEmpty(Mono.defer(() -> createPatronAtSupplier(patronRequest, supplierRequest)));
	}

	private Mono<SupplierRequest> placeRequestAtSupplier(PatronRequest patronRequest,
		SupplierRequest supplierRequest, PatronIdentity patronIdentity) {
		log.debug("placeRequestAtSupplier {}, {}", patronRequest.getId(), patronIdentity.getId());

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(client -> client.placeHoldRequest(patronIdentity.getLocalId(), "i",
				supplierRequest.getLocalItemId(), patronRequest.getPickupLocationCode()))
			.map(tuple -> {
				supplierRequest.setLocalId(tuple.getT1());
				supplierRequest.setLocalStatus(tuple.getT2());
				supplierRequest.setStatusCode(SupplierRequestStatusCode.PLACED);
				return supplierRequest;
			});
	}

	private Mono<PatronIdentity> checkIfPatronExistsAtSupplier(PatronRequest patronRequest,
		SupplierRequest supplierRequest) {
		log.debug("checkSupplierFor {}, {}", patronRequest.getId(), supplierRequest.getId());

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(hostLmsClient ->
				hostLmsClient.patronFind(patronService.getUniqueIdStringFor(patronRequest.getPatron())))
			.flatMap(localId ->
				patronService.checkForPatronIdentity(patronRequest.getPatron(), supplierRequest.getHostLmsCode(), localId));
	}

	private Mono<PatronIdentity> createPatronAtSupplier(PatronRequest patronRequest, SupplierRequest supplierRequest) {
		log.debug("createPatronForSupplier {}, {}", patronRequest.getId(), supplierRequest.getId());

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(client -> client.createPatron(patronService.getUniqueIdStringFor(patronRequest.getPatron()),
					patronTypeService.determinePatronType()))
			.flatMap(localId -> patronService.checkForPatronIdentity(patronRequest.getPatron(),
					supplierRequest.getHostLmsCode(), localId));
	}

	public Mono<Tuple2<PatronRequest, SupplierRequest>> getSupplierRequestTuple(PatronRequest patronRequest) {
		return supplierRequestService.findSupplierRequestFor(patronRequest).map(sr -> Tuples.of(patronRequest, sr));
	}
}

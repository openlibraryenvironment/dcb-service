package org.olf.reshare.dcb.request.fulfilment;

import static reactor.function.TupleUtils.function;

import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.olf.reshare.dcb.core.interaction.HostLmsHold;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
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

		return findSupplierRequestFor(patronRequest)
			.flatMap(function(this::checkAndCreatePatronAtSupplier))
			.flatMap(function(this::placeRequestAtSupplier))
			.flatMap(function(this::updateSupplierRequest))
			.map(PatronRequest::placedAtSupplyingAgency);
	}

	private Mono<Tuple3<PatronRequest, SupplierRequest, PatronIdentity>> checkAndCreatePatronAtSupplier(
		PatronRequest patronRequest, SupplierRequest supplierRequest) {

		return checkIfPatronExistsAtSupplier(patronRequest, supplierRequest)
			.switchIfEmpty(Mono.defer(() -> createPatronAtSupplier(patronRequest, supplierRequest)))
			.map(patronIdentity -> Tuples.of(patronRequest, supplierRequest, patronIdentity));
	}

	private Mono<Tuple2<SupplierRequest, PatronRequest>> placeRequestAtSupplier(PatronRequest patronRequest,
		SupplierRequest supplierRequest, PatronIdentity patronIdentity) {

		log.debug("placeRequestAtSupplier {}, {}", patronRequest.getId(), patronIdentity.getId());

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(client -> client.placeHoldRequest(patronIdentity.getLocalId(), "i",
				supplierRequest.getLocalItemId(), patronRequest.getPickupLocationCode()))
			.map(function(supplierRequest::placed))
			.map(changedSupplierRequest -> Tuples.of(supplierRequest, patronRequest));
	}

	private Mono<PatronRequest> updateSupplierRequest(
		SupplierRequest supplierRequest, PatronRequest patronRequest) {

		return supplierRequestService.updateSupplierRequest(supplierRequest)
			.thenReturn(patronRequest);
	}

	private Mono<PatronIdentity> checkIfPatronExistsAtSupplier(PatronRequest patronRequest,
		SupplierRequest supplierRequest) {

		log.debug("checkSupplierFor {}, {}", patronRequest.getId(), supplierRequest.getId());

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(hostLmsClient ->
				hostLmsClient.patronFind(patronService.getUniqueIdStringFor(patronRequest.getPatron())))
			.flatMap(localId ->
				checkForPatronIdentity(patronRequest, supplierRequest.getHostLmsCode(), localId));
	}

	private Mono<PatronIdentity> createPatronAtSupplier(PatronRequest patronRequest,
		SupplierRequest supplierRequest) {

		log.debug("createPatronForSupplier {}, {}", patronRequest.getId(), supplierRequest.getId());

		final var hostLmsCode = supplierRequest.getHostLmsCode();

		return hostLmsService.getClientFor(hostLmsCode)
			.zipWhen(client -> determinePatronType(hostLmsCode), Tuples::of)
			.flatMap(function((client, patronType) -> createPatronAtSupplier(patronRequest, client, patronType)))
			.flatMap(localId -> checkForPatronIdentity(patronRequest, hostLmsCode, localId));
	}

	private Mono<String> createPatronAtSupplier(PatronRequest patronRequest,
		HostLmsClient client, String patronType) {

		return client.createPatron(
			patronService.getUniqueIdStringFor(patronRequest.getPatron()), 
			patronType);
	}

	private Mono<PatronIdentity> checkForPatronIdentity(PatronRequest patronRequest,
		String hostLmsCode, String localId) {

		return patronService.checkForPatronIdentity(patronRequest.getPatron(),
			hostLmsCode, localId);
	}

	private Mono<Tuple2<PatronRequest, SupplierRequest>> findSupplierRequestFor(
		PatronRequest patronRequest) {

		return supplierRequestService.findSupplierRequestFor(patronRequest)
			.map(supplierRequest -> Tuples.of(patronRequest, supplierRequest));
	}

	private Mono<String> determinePatronType(String hostLmsCode) {
                log.warn("ToDo - this function needs to consume the patron type for the patron");
		return patronTypeService.determinePatronType(hostLmsCode);
	}

	public Mono<HostLmsHold> getHold(String hostLmsCode, String holdId) {
		log.debug("getHoldStatus({},{})",hostLmsCode,holdId);
		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap( client -> client.getHold(holdId) );
	}

}

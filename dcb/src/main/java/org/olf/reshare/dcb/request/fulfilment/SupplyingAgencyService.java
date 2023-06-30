package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.HostLmsHold;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import static reactor.function.TupleUtils.function;

@Prototype
public class SupplyingAgencyService {
	private static final Logger log = LoggerFactory.getLogger(SupplyingAgencyService.class);

	private final HostLmsService hostLmsService;
	private final SupplierRequestService supplierRequestService;
	private final PatronService patronService;
	private final PatronTypeService patronTypeService;
	private final PatronRequestTransitionErrorService errorService;

	public SupplyingAgencyService(
		HostLmsService hostLmsService, SupplierRequestService supplierRequestService,
		PatronService patronService, PatronTypeService patronTypeService,
		PatronRequestTransitionErrorService errorService) {

		this.hostLmsService = hostLmsService;
		this.supplierRequestService = supplierRequestService;
		this.patronService = patronService;
		this.patronTypeService = patronTypeService;
		this.errorService = errorService;
	}

	public Mono<PatronRequest> placePatronRequestAtSupplyingAgency(PatronRequest patronRequest) {
		log.debug("placePatronRequestAtSupplyingAgency {}", patronRequest.getId());

		return findSupplierRequestFor(patronRequest)
			.flatMap(function(this::checkAndCreatePatronAtSupplier))
			.flatMap(function(this::placeRequestAtSupplier))
			.flatMap(function(this::updateSupplierRequest))
			.map(PatronRequest::placedAtSupplyingAgency)
			.onErrorResume(error -> errorService.moveRequestToErrorStatus(error, patronRequest));
	}

	private Mono<Tuple3<PatronRequest, SupplierRequest, PatronIdentity>> checkAndCreatePatronAtSupplier(
		PatronRequest patronRequest, SupplierRequest supplierRequest) {

                log.debug("checkAndCreatePatronAtSupplier {} {}",patronRequest,supplierRequest);

		return upsertPatronIdentityAtSupplier(patronRequest,supplierRequest)
                        .map(patronIdentity -> { supplierRequest.setVirtualIdentity(patronIdentity); return patronIdentity; } )
			.map(patronIdentity -> Tuples.of(patronRequest, supplierRequest, patronIdentity));
	}

	private Mono<Tuple2<SupplierRequest, PatronRequest>> placeRequestAtSupplier(
		PatronRequest patronRequest,
		SupplierRequest supplierRequest, 
		PatronIdentity patronIdentityAtSupplier) {

		log.debug("placeRequestAtSupplier {}, {}", patronRequest.getId(), patronIdentityAtSupplier.getId());

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(client -> client.placeHoldRequest(patronIdentityAtSupplier.getLocalId(), "i",
				supplierRequest.getLocalItemId(), patronRequest.getPickupLocationCode()))
			.map(function(supplierRequest::placed))
			.map(changedSupplierRequest -> Tuples.of(supplierRequest, patronRequest));
	}

	private Mono<PatronRequest> updateSupplierRequest(
		SupplierRequest supplierRequest, PatronRequest patronRequest) {

		return supplierRequestService.updateSupplierRequest(supplierRequest)
			.thenReturn(patronRequest);
	}

	private Mono<PatronIdentity> upsertPatronIdentityAtSupplier(PatronRequest patronRequest, SupplierRequest supplierRequest) {
		return checkIfPatronExistsAtSupplier(patronRequest, supplierRequest)
                        .switchIfEmpty(Mono.defer(() -> createPatronAtSupplier(patronRequest, supplierRequest)));
	}

	private Mono<PatronIdentity> checkIfPatronExistsAtSupplier(PatronRequest patronRequest,
		SupplierRequest supplierRequest) {

		log.debug("checkIfPatronExistsAtSupplier req={}, supplierSystemCode={}", patronRequest.getId(), supplierRequest.getHostLmsCode());

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.patronFind(patronService.getUniqueIdStringFor(patronRequest.getPatron())))
			.flatMap(localId -> checkForPatronIdentity(patronRequest, supplierRequest.getHostLmsCode(), localId));

                // We should verify that the patron type (and hence mapped type) has not changed - something like...
                // determinePatronType(supplierRequest.getHostLmsCode(), requestingPatronIdentity)
                // .map( suppler_ptype -> { patron_identity.setLocalPtype(supplier_ptype); return patron_identity }
	}

	private Mono<PatronIdentity> getRequestingIdentity(PatronRequest patronRequest) {
		log.debug("getRequestingIdentity was called with: {}", patronRequest.getPatron());
		if ( ( patronRequest != null ) &&
			( patronRequest.getRequestingIdentity() != null ) ) {
				log.debug("Attempting to locate patron identity for {}",patronRequest.getRequestingIdentity().getId());
				return patronService.getPatronIdentityById(patronRequest.getRequestingIdentity().getId());
		}
		else {
			log.warn("getRequestingIdentity was unable to find a requesting identity. Returning empty()");
			return Mono.empty();
		}
	}

	private Mono<PatronIdentity> createPatronAtSupplier(PatronRequest patronRequest,
		SupplierRequest supplierRequest) {

		log.debug("createPatronAtSupplier {}, {}", patronRequest.getId(), supplierRequest.getId());

		final var hostLmsCode = supplierRequest.getHostLmsCode();

		return hostLmsService.getClientFor(hostLmsCode)
			.zipWhen(client -> getRequestingIdentity(patronRequest), Tuples::of)
			.flatMap(function((client,requestingIdentity) -> createPatronAtSupplier(patronRequest, client, requestingIdentity, hostLmsCode)))
			.flatMap(localId -> checkForPatronIdentity(patronRequest, hostLmsCode, localId));
	}

	private Mono<String> createPatronAtSupplier(
                        PatronRequest patronRequest,
		        HostLmsClient client, 
                        PatronIdentity requestingPatronIdentity,
                        String supplierHostLmsCode) {
                // Using the patron type from the patrons "Home" patronIdentity, look up what the equivalent patron type is at
                // the supplying system. Then create a patron in the supplying system using that type value. 
		return determinePatronType(supplierHostLmsCode, requestingPatronIdentity)
                       .flatMap(patronType -> client.createPatron( patronService.getUniqueIdStringFor(patronRequest.getPatron()), patronType));
	}

	private Mono<PatronIdentity> checkForPatronIdentity(PatronRequest patronRequest,
		String hostLmsCode, String localId) {

		return patronService.checkForPatronIdentity(patronRequest.getPatron(), hostLmsCode, localId);
	}

	private Mono<Tuple2<PatronRequest, SupplierRequest>> findSupplierRequestFor(
		PatronRequest patronRequest) {

                log.debug("findSupplierRequestFor {}",patronRequest);

		return supplierRequestService.findSupplierRequestFor(patronRequest)
			.map(supplierRequest -> Tuples.of(patronRequest, supplierRequest));
	}

	private Mono<String> determinePatronType(String supplyingHostLmsCode, PatronIdentity requestingIdentity) {
                if ( requestingIdentity != null ) {
                        // We need to look up the requestingHostLmsCode and not pass supplyingHostLmsCode
		        return patronTypeService.determinePatronType(supplyingHostLmsCode, 
                                supplyingHostLmsCode, 
                                requestingIdentity.getLocalPtype());
                }
                else {
                        return Mono.empty();
                }
	}

	public Mono<HostLmsHold> getHold(String hostLmsCode, String holdId) {
		log.debug("getHoldStatus({},{})",hostLmsCode,holdId);
		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap( client -> client.getHold(holdId) );
	}

}

package org.olf.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsHold;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.time.Instant;

import java.util.List;
import java.util.Map;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.request.fulfilment.PatronRequestStatusConstants.*;
import static reactor.function.TupleUtils.function;
import static reactor.util.function.Tuples.of;

@Prototype
public class SupplyingAgencyService {
	private static final Logger log = LoggerFactory.getLogger(SupplyingAgencyService.class);

	private final HostLmsService hostLmsService;
	private final SupplierRequestService supplierRequestService;
	private final PatronService patronService;
	private final PatronTypeService patronTypeService;
	private final PatronRequestTransitionErrorService errorService;
	private final PatronRequestAuditService patronRequestAuditService;

	public SupplyingAgencyService(
		HostLmsService hostLmsService, SupplierRequestService supplierRequestService,
		PatronService patronService, PatronTypeService patronTypeService,
		PatronRequestTransitionErrorService errorService,
		PatronRequestAuditService patronRequestAuditService) {

		this.hostLmsService = hostLmsService;
		this.supplierRequestService = supplierRequestService;
		this.patronService = patronService;
		this.patronTypeService = patronTypeService;
		this.errorService = errorService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	public Mono<PatronRequest> placePatronRequestAtSupplyingAgency(PatronRequest patronRequest) {
		log.debug("placePatronRequestAtSupplyingAgency {}", patronRequest.getId());

		return findSupplierRequestFor(patronRequest)
			.flatMap(function(this::checkAndCreatePatronAtSupplier))
			.flatMap(function(this::placeRequestAtSupplier))
			.flatMap(function(this::updateSupplierRequest))
			.map(PatronRequest::placedAtSupplyingAgency)
			.flatMap(this::createAuditEntry)
			.onErrorResume(error -> addAuditLogEntry(error, patronRequest));
	}

	private Mono<PatronRequest> addAuditLogEntry(Throwable error, PatronRequest patronRequest) {
		var audit = createPatronRequestAudit(patronRequest).briefDescription(error.getMessage()).build();
		return errorService.recordError(error, audit);
	}

	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {
		var audit = createPatronRequestAudit(patronRequest).build();
		return patronRequestAuditService.audit(audit, false).thenReturn(patronRequest);
	}

	private PatronRequestAudit.PatronRequestAuditBuilder createPatronRequestAudit(
		PatronRequest patronRequest) {
		return PatronRequestAudit.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.auditDate(Instant.now())
			.fromStatus(RESOLVED)
			.toStatus(REQUEST_PLACED_AT_SUPPLYING_AGENCY);
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
			.flatMap(client -> this.placeHoldRequest(patronIdentityAtSupplier, supplierRequest,patronRequest, client) )
			.doOnSuccess(result -> log.info("Hold placed({})", result))
			.map(function(supplierRequest::placed))
			.map(changedSupplierRequest -> Tuples.of(supplierRequest, patronRequest));
	}

        private Mono<Tuple2<String, String>> placeHoldRequest(
                PatronIdentity patronIdentity, 
                SupplierRequest supplierRequest,
                PatronRequest patronRequest,
                HostLmsClient client) {

                String requestedThingType = "i"; // Default requst an item
                String requestedThingId = supplierRequest.getLocalItemId(); // Default item ID

                Map<String, Object> cfg = client.getHostLms().getClientConfig();
                if ( ( cfg != null ) && ( cfg.get("holdPolicy") != null ) && ( cfg.get("holdPolicy").equals("title")  ) ) {
                        log.info("Client is configured for title level hold policy - switching");
			requestedThingType = "b";
			requestedThingId = supplierRequest.getLocalBibId();
                }

		String note = "Consortial Hold. tno="+patronRequest.getId();

                // Depending upon client configuration, we may need to place an item or a title level hold
		log.debug("Call client.placeHoldRequest");
                return client.placeHoldRequest(patronIdentity.getLocalId(), requestedThingType, requestedThingId, patronRequest.getPickupLocationCode(), note,
			patronRequest.getId().toString());
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
			.flatMap(hostLmsClient ->
					hostLmsClient.patronFind(patronService.getUniqueIdStringFor(patronRequest.getPatron())))
			.flatMap(tuple ->
				checkPatronType(tuple.getT1(), tuple.getT2(), patronRequest, supplierRequest.getHostLmsCode()))
			.flatMap(function((localId, patronType) ->
				checkForPatronIdentity(patronRequest, supplierRequest.getHostLmsCode(), localId, patronType)));
	}

	private Mono<Tuple2<String, String>> checkPatronType(String localId, String patronType,
			PatronRequest patronRequest, String supplierHostLmsCode) {
		return getRequestingIdentity(patronRequest)
			.flatMap(requestingIdentity -> determinePatronType(supplierHostLmsCode, requestingIdentity))
			.filter(dcbPatronType -> dcbPatronType.equals(patronType))
			.switchIfEmpty(Mono.defer(() -> updateVirtualPatron(supplierHostLmsCode, localId, patronType)))
			.map(updatedPatronType -> Tuples.of(localId, updatedPatronType));
	}

	private Mono<String> updateVirtualPatron(String supplierHostLmsCode, String localId, String patronType) {
		log.debug("updateVirtualPatron {}, {}", localId, patronType);
		return hostLmsService.getClientFor(supplierHostLmsCode)
			.flatMap(hostLmsClient -> hostLmsClient.updatePatron(localId, patronType))
			.map(Patron::getLocalPatronType);
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
			.flatMap(function((client,requestingIdentity) ->
				createPatronAtSupplier(patronRequest, client, requestingIdentity, hostLmsCode)))
			.flatMap(function((localId, patronType) ->
				checkForPatronIdentity(patronRequest, hostLmsCode, localId, patronType)));
	}

	private Mono<Tuple2<String, String>> createPatronAtSupplier(
                        PatronRequest patronRequest,
		        HostLmsClient client, 
                        PatronIdentity requestingPatronIdentity,
                        String supplierHostLmsCode) {
                // Using the patron type from the patrons "Home" patronIdentity, look up what the equivalent patron type is at
                // the supplying system. Then create a patron in the supplying system using that type value.

		return determinePatronType(supplierHostLmsCode, requestingPatronIdentity)
			.flatMap(patronType -> client.createPatron(
				Patron.builder()
					.uniqueIds( stringToList(patronService.getUniqueIdStringFor(patronRequest.getPatron()) ))
					.localBarcodes( stringToList(requestingPatronIdentity.getLocalBarcode()))
					.localPatronType( patronType )
					.build())
				.map(createdPatron -> Tuples.of(createdPatron, patronType)));
	}

	private List<String> stringToList(String string) {return string != null ? List.of(string) : null;}

	private Mono<PatronIdentity> checkForPatronIdentity(PatronRequest patronRequest,
		String hostLmsCode, String localId, String localPType) {

		return patronService.checkForPatronIdentity(patronRequest.getPatron(),
				hostLmsCode, localId, localPType);
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

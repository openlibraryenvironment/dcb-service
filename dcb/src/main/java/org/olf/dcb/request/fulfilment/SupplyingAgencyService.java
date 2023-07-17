package org.olf.dcb.request.fulfilment;

import static reactor.function.TupleUtils.function;

import java.util.List;
import java.util.Map;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsHold;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import lombok.experimental.Accessors;
import lombok.Data;

@Prototype
public class SupplyingAgencyService {
	private static final Logger log = LoggerFactory.getLogger(SupplyingAgencyService.class);

	private final HostLmsService hostLmsService;
	private final SupplierRequestService supplierRequestService;
	private final PatronService patronService;
	private final PatronTypeService patronTypeService;
	
	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;
	private final PatronRequestAuditService patronRequestAuditService;

        @Data
        @Accessors(chain=true)
        private class PlaceSupplierRequestContext {
                String patronAgencyCode;
                String patronSystemCode;
                String pickupAgencyCode;
                String pickupSystemCode;
                String lenderAgencyCode;
                String lenderSystemCode;

                PatronIdentity patronHomeIdentity;
                PatronIdentity patronVirtualIdentity;

                PatronRequest patronRequest;
                SupplierRequest supplierRequest;

                String supplierHoldId;
                String supplierHoldStatus;
        }


	public SupplyingAgencyService(
		HostLmsService hostLmsService, SupplierRequestService supplierRequestService,
		PatronService patronService, PatronTypeService patronTypeService,
		PatronRequestRepository patronRequestRepository,
		PatronRequestAuditService patronRequestAuditService, BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider) {

		this.hostLmsService = hostLmsService;
		this.supplierRequestService = supplierRequestService;
		this.patronService = patronService;
		this.patronTypeService = patronTypeService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	public Mono<PatronRequest> placePatronRequestAtSupplyingAgency(PatronRequest patronRequest) {
		log.debug("placePatronRequestAtSupplyingAgency {}", patronRequest.getId());

                // In order to place a request at the supplying agency we need to know
                // The agency-code and system-code of the patron, 
                // the agency-code and system-code of the supplier
                // The agency-code and system code of the pickup location
                //
                // If all three are in the same system this is a local hold - variant 1
                // If the pickup system and the borrower system are the same then this is a simple 2 party loan
                // psrc If the loan system and the pickup system are the same it's also a simple (Ish) 2 party loan
                // If the pickup system and the borrower system and the lending system are all different it's a three-party loan 
                PlaceSupplierRequestContext src = new PlaceSupplierRequestContext().setPatronRequest(patronRequest);

                return findSupplierRequest(src)
                        .flatMap(this::checkAndCreatePatronAtSupplier)
                        .flatMap(this::placeRequestAtSupplier)
                        .flatMap(this::updateSupplierRequest)
                        .map(PatronRequest::placedAtSupplyingAgency)
			.flatMap(this::createAuditEntry)
			.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest));

		/*
		return findSupplierRequestFor(patronRequest)
			.flatMap(function(this::checkAndCreatePatronAtSupplier))
			.flatMap(function(this::placeRequestAtSupplier))
			.flatMap(function(this::updateSupplierRequest))
			.map(PatronRequest::placedAtSupplyingAgency)
			.flatMap(this::createAuditEntry)
			.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest));
		*/
	}

	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {
		
		if (patronRequest.getStatus() == Status.ERROR) return Mono.just(patronRequest);
		
		return patronRequestAuditService
			.addAuditEntry(patronRequest, Status.RESOLVED, Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.thenReturn(patronRequest);
	}

	private Mono<PlaceSupplierRequestContext> checkAndCreatePatronAtSupplier(PlaceSupplierRequestContext psrc) {

		log.debug("checkAndCreatePatronAtSupplier {}",psrc);

		PatronRequest patronRequest = psrc.getPatronRequest();
		SupplierRequest supplierRequest = psrc.getSupplierRequest();

		return upsertPatronIdentityAtSupplier(patronRequest,supplierRequest)
			.map(patronIdentity -> { 
				psrc.setPatronVirtualIdentity(patronIdentity); 
				supplierRequest.setVirtualIdentity(patronIdentity);
				return psrc; 
			});
	}

	private Mono<PlaceSupplierRequestContext> placeRequestAtSupplier(PlaceSupplierRequestContext psrc) {

		log.debug("placeRequestAtSupplier {}", psrc);

		PatronRequest patronRequest = psrc.getPatronRequest();
		SupplierRequest supplierRequest = psrc.getSupplierRequest();
		PatronIdentity patronIdentityAtSupplier = psrc.getPatronVirtualIdentity();

		assert ( ( patronRequest != null ) && ( supplierRequest != null ) && ( patronIdentityAtSupplier != null ) );

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(client -> this.placeHoldRequest(patronIdentityAtSupplier, supplierRequest, patronRequest, client) )
			.doOnSuccess(result -> log.info("Hold placed({})", result))
			.map(function(supplierRequest::placed))
			.thenReturn(psrc);
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
                

	private Mono<PatronRequest> updateSupplierRequest(PlaceSupplierRequestContext psrc) {

		SupplierRequest supplierRequest = psrc.getSupplierRequest();
		PatronRequest patronRequest = psrc.getPatronRequest();

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

        // Remember that @Accessors means that setSupplierRequest returns this
        private Mono<PlaceSupplierRequestContext> findSupplierRequest(PlaceSupplierRequestContext ctx) {
                return supplierRequestService.findSupplierRequestFor(ctx.getPatronRequest())
                        .map(supplierRequest -> ctx.setSupplierRequest(supplierRequest));
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

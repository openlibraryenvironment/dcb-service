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

// import lombok.experimental.Accessors;
// import lombok.Data;

@Prototype
public class SupplyingAgencyService {
	private static final Logger log = LoggerFactory.getLogger(SupplyingAgencyService.class);

	private final HostLmsService hostLmsService;
	private final SupplierRequestService supplierRequestService;
	private final PatronService patronService;
	private final PatronTypeService patronTypeService;
        private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	
	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;
	private final PatronRequestAuditService patronRequestAuditService;

	public SupplyingAgencyService(
		HostLmsService hostLmsService, SupplierRequestService supplierRequestService,
		PatronService patronService, PatronTypeService patronTypeService,
		PatronRequestRepository patronRequestRepository,
		PatronRequestAuditService patronRequestAuditService, BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
                RequestWorkflowContextHelper requestWorkflowContextHelper) {

		this.hostLmsService = hostLmsService;
		this.supplierRequestService = supplierRequestService;
		this.patronService = patronService;
		this.patronTypeService = patronTypeService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.patronRequestAuditService = patronRequestAuditService;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
	}

	public Mono<PatronRequest> placePatronRequestAtSupplyingAgency(PatronRequest patronRequest) {
		log.debug("placePatronRequestAtSupplyingAgency... {}", patronRequest.getId());

                // In order to place a request at the supplying agency we need to know
                // The agency-code and system-code of the patron, 
                // the agency-code and system-code of the supplier
                // The agency-code and system code of the pickup location
                //
                // If all three are in the same system this is a local hold - variant 1
                // If the pickup system and the borrower system are the same then this is a simple 2 party loan
                // psrc If the loan system and the pickup system are the same it's also a simple (Ish) 2 party loan
                // If the pickup system and the borrower system and the lending system are all different it's a three-party loan 
                //
                // 1. Local - lender, pickup and borrower are same system
                // 2. Pickup at lender - Patron will pick item up from lender system, but borrower system is different
                // 3. Pickup at borrower - Patron will pick item up from one of their home libraries, borrower system is different
                // 4. PUA - Lender, Pickup and Borrower systems are all different.

                // For now, the collect method just attaches the suppier request, but more coming
                return requestWorkflowContextHelper.fromPatronRequest(patronRequest)
                        .flatMap(this::checkAndCreatePatronAtSupplier)
                        .flatMap(this::placeRequestAtSupplier)
                        .flatMap(this::updateSupplierRequest)
                        .map(PatronRequest::placedAtSupplyingAgency)
			.flatMap(this::createAuditEntry)
			.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest));
	}

	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {

		log.debug("createAuditEntry {}", patronRequest.getStatus());

		
		if (patronRequest.getStatus() == Status.ERROR) return Mono.just(patronRequest);
		
		return patronRequestAuditService
			.addAuditEntry(patronRequest, Status.RESOLVED, Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.thenReturn(patronRequest);
	}

	private Mono<RequestWorkflowContext> checkAndCreatePatronAtSupplier(RequestWorkflowContext psrc) {

		log.debug("checkAndCreatePatronAtSupplier");

		PatronRequest patronRequest = psrc.getPatronRequest();
		SupplierRequest supplierRequest = psrc.getSupplierRequest();

		// return upsertPatronIdentityAtSupplier(patronRequest,supplierRequest)
		return upsertPatronIdentityAtSupplier(psrc)
			.map(patronIdentity -> { 
				psrc.setPatronVirtualIdentity(patronIdentity); 
				supplierRequest.setVirtualIdentity(patronIdentity);
				return psrc; 
			});
	}

	private Mono<RequestWorkflowContext> placeRequestAtSupplier(RequestWorkflowContext psrc) {

		log.debug("placeRequestAtSupplier");

		PatronRequest patronRequest = psrc.getPatronRequest();
		SupplierRequest supplierRequest = psrc.getSupplierRequest();
		PatronIdentity patronIdentityAtSupplier = psrc.getPatronVirtualIdentity();

                // Validate that the context contains all the information we need to execute this step
		if ( ( patronRequest == null ) ||
                     ( supplierRequest == null ) ||
                     ( patronIdentityAtSupplier == null ) ||
                     ( psrc.getPickupAgencyCode() == null ) )
                        throw new RuntimeException("Invalid RequestWorkflowContext "+psrc.toString());

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(client -> this.placeHoldRequest(client, psrc) )
			// .doOnSuccess(result -> log.info("Hold placed({})", result))
			.map(function(supplierRequest::placed))
			.thenReturn(psrc);
	}

        private Mono<Tuple2<String, String>> placeHoldRequest(
                HostLmsClient client,
                RequestWorkflowContext psrc) {

		log.debug("placeHoldRequest");

		PatronRequest patronRequest = psrc.getPatronRequest();
		SupplierRequest supplierRequest = psrc.getSupplierRequest();
		PatronIdentity patronIdentityAtSupplier = psrc.getPatronVirtualIdentity();

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
		// log.debug("Call client.placeHoldRequest");
                return client.placeHoldRequest(patronIdentityAtSupplier.getLocalId(), 
                        requestedThingType, 
                        requestedThingId, 
                        psrc.getPickupAgencyCode(),      // patronRequest.getPickupLocationCode(), 
                        note,
			patronRequest.getId().toString());
        }
                

	private Mono<PatronRequest> updateSupplierRequest(RequestWorkflowContext psrc) {

		log.debug("updateSupplierRequest");

		SupplierRequest supplierRequest = psrc.getSupplierRequest();
		PatronRequest patronRequest = psrc.getPatronRequest();

		return supplierRequestService.updateSupplierRequest(supplierRequest)
			.thenReturn(patronRequest);
	}

	private Mono<PatronIdentity> upsertPatronIdentityAtSupplier(RequestWorkflowContext psrc) {

		log.debug("upsertPatronIdentityAtSupplier");

                PatronRequest patronRequest = psrc.getPatronRequest();
                SupplierRequest supplierRequest = psrc.getSupplierRequest();

		return checkIfPatronExistsAtSupplier(psrc)
                        .switchIfEmpty(Mono.defer(() -> createPatronAtSupplier(patronRequest, supplierRequest)));
	}

	private Mono<PatronIdentity> checkIfPatronExistsAtSupplier(RequestWorkflowContext psrc) {

		log.debug("checkIfPatronExistsAtSupplier");

                PatronRequest patronRequest = psrc.getPatronRequest();
                SupplierRequest supplierRequest = psrc.getSupplierRequest();
		// log.debug("checkIfPatronExistsAtSupplier req={}, supplierSystemCode={}", patronRequest.getId(), supplierRequest.getHostLmsCode());

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

		log.debug("checkPatronType {}, {}", localId, patronType);

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
		// log.debug("getRequestingIdentity was called with: {}", patronRequest.getPatron());
		if ( ( patronRequest != null ) &&
			( patronRequest.getRequestingIdentity() != null ) ) {
				// log.debug("Attempting to locate patron identity for {}",patronRequest.getRequestingIdentity().getId());
				return patronService.getPatronIdentityById(patronRequest.getRequestingIdentity().getId());
		}
		else {
			log.warn("getRequestingIdentity was unable to find a requesting identity. Returning empty()");
			return Mono.empty();
		}
	}

	private Mono<PatronIdentity> createPatronAtSupplier(PatronRequest patronRequest, SupplierRequest supplierRequest) {

		final var hostLmsCode = supplierRequest.getHostLmsCode();

		log.debug("createPatronAtSupplier prid={}, srid={} supplierCode={}", patronRequest.getId(), supplierRequest.getId(),hostLmsCode);

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

		log.debug("createPatronAtSupplier2");

		return determinePatronType(supplierHostLmsCode, requestingPatronIdentity)
			.flatMap(patronType -> client.createPatron(
				Patron.builder()
					.uniqueIds( stringToList(patronService.getUniqueIdStringFor(patronRequest.getPatron()) ))
					.localBarcodes( stringToList(requestingPatronIdentity.getLocalBarcode()))
					.localPatronType( patronType )
					.build())
				.map(createdPatron -> Tuples.of(createdPatron, patronType)));
	}

	private List<String> stringToList(String string) {
		log.debug("stringToList {}",string);
                return string != null ? List.of(string) : null;
        }

	private Mono<PatronIdentity> checkForPatronIdentity(PatronRequest patronRequest,
		String hostLmsCode, String localId, String localPType) {

		return patronService.checkForPatronIdentity(patronRequest.getPatron(),
				hostLmsCode, localId, localPType);
	}

	private Mono<String> determinePatronType(String supplyingHostLmsCode, PatronIdentity requestingIdentity) {

                log.debug("determinePatronType");

                if ( supplyingHostLmsCode == null || requestingIdentity == null || requestingIdentity.getHostLms() == null || 
                        requestingIdentity.getHostLms().getCode() == null ) {
                        throw new RuntimeException("Missing patron data - unable to determine patron type at supplier:"+supplyingHostLmsCode);
                }

                // log.debug("determinePatronType {} {} {} requesting identity present",supplyingHostLmsCode, requestingIdentity.getHostLms().getCode(),
                //        requestingIdentity.getLocalPtype());
                
                // We need to look up the requestingHostLmsCode and not pass supplyingHostLmsCode
		return patronTypeService.determinePatronType(supplyingHostLmsCode, 
                        requestingIdentity.getHostLms().getCode(),
                        requestingIdentity.getLocalPtype());
	}

	public Mono<HostLmsHold> getHold(String hostLmsCode, String holdId) {
		log.debug("getHold({},{})",hostLmsCode,holdId);
		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap( client -> client.getHold(holdId) );
	}
}

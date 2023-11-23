package org.olf.dcb.request.fulfilment;

import static reactor.function.TupleUtils.function;

import java.util.Arrays;
import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsHold;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
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
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;

	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	public SupplyingAgencyService(
		HostLmsService hostLmsService, SupplierRequestService supplierRequestService,
		PatronService patronService, PatronTypeService patronTypeService,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
		RequestWorkflowContextHelper requestWorkflowContextHelper) {

		this.hostLmsService = hostLmsService;
		this.supplierRequestService = supplierRequestService;
		this.patronService = patronService;
		this.patronTypeService = patronTypeService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
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

		return requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(this::checkAndCreatePatronAtSupplier)
			.flatMap(this::placeRequestAtSupplier)
			.flatMap(this::setPatronRequestWorkflow)
			.flatMap(this::updateSupplierRequest)
			.map(PatronRequest::placedAtSupplyingAgency)
			// We do this work a level up at PlacePatronRequestAtSupplyingAgencyStateTransition.createAuditEntry
			// commenting out as of 2023-08-16. If audit log looks good will remove entirely.
			.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest));
	}

	public Mono<PatronRequest> cleanUp(PatronRequest patronRequest) {
		return Mono.just(patronRequest);
	}

	private Mono<RequestWorkflowContext> checkAndCreatePatronAtSupplier(RequestWorkflowContext psrc) {
		log.debug("checkAndCreatePatronAtSupplier");
		SupplierRequest supplierRequest = psrc.getSupplierRequest();

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
		if ((patronRequest == null) ||
			(supplierRequest == null) ||
			(patronIdentityAtSupplier == null) ||
			(psrc.getPickupAgencyCode() == null))
			throw new RuntimeException("Invalid RequestWorkflowContext " + psrc);

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(client -> this.placeHoldRequest(client, psrc) )
			// .doOnSuccess(result -> log.info("Hold placed({})", result))
			.map(function(supplierRequest::placed))
			.thenReturn(psrc);
	}

	private Mono<Tuple2<String, String>> placeHoldRequest(HostLmsClient client,
		RequestWorkflowContext context) {

		log.debug("placeHoldRequest");

		PatronRequest patronRequest = context.getPatronRequest();
		SupplierRequest supplierRequest = context.getSupplierRequest();
		PatronIdentity patronIdentityAtSupplier = context.getPatronVirtualIdentity();

		final String recordNumber;
		final String recordType;

		if (client.useTitleLevelRequest()) {
			log.debug("place title level request for ID {}", supplierRequest.getLocalBibId());
			recordType = "b";
			recordNumber = supplierRequest.getLocalBibId();
		}
		else if (client.useItemLevelRequest()) {
			log.debug("place item level request for ID {}", supplierRequest.getLocalItemId());
			recordType = "i";
			recordNumber = supplierRequest.getLocalItemId();
		}
		else {
			// Using runtime error until this logic is moved behind the host LMS client boundary
			return Mono.error(new RuntimeException(
				"Invalid hold policy for Host LMS \"" + client.getHostLms().getCode() + "\""));
		}

		String note = "Consortial Hold. tno="+patronRequest.getId();

		return client.placeHoldRequestNonTuple(patronIdentityAtSupplier.getLocalId(),
			recordType, recordNumber, context.getPickupAgencyCode(),
			note, patronRequest.getId().toString())
			.map(response -> Tuples.of(response.getLocalId(), response.getLocalStatus()));
	}

	private Mono<PatronRequest> updateSupplierRequest(RequestWorkflowContext psrc) {
		log.debug("updateSupplierRequest");

		SupplierRequest supplierRequest = psrc.getSupplierRequest();
		PatronRequest patronRequest = psrc.getPatronRequest();

		return supplierRequestService.updateSupplierRequest(supplierRequest)
			.thenReturn(patronRequest);
	}


	// Depending upon the particular setup (1, 2 or three parties) we need to take different actions in different scenarios.
	// Here we work out which particular workflow is in force and set a value on the patron request for easy reference.
	// This can change as we select different suppliers, so we recalculate for each new supplier.
	private Mono<RequestWorkflowContext> setPatronRequestWorkflow(RequestWorkflowContext psrc) {
		log.debug("setPatronRequestWorkflow for {}", psrc);
		if ((psrc.getPatronAgencyCode().equals(psrc.getLenderAgencyCode())) &&
			(psrc.getPatronAgencyCode().equals(psrc.getPickupAgencyCode()))) {
			// Case 1 : Purely local request
			psrc.getPatronRequest().setActiveWorkflow("RET-LOCAL");
		} else if (psrc.getPatronAgencyCode().equals(psrc.getPickupAgencyCode())) {
			// Case 2 : Remote lender, patron picking up from a a library in their home system
			psrc.getPatronRequest().setActiveWorkflow("RET-STD");
		} else {
			// Case 3 : Three legged transaction - Lender, Pickup, Borrower
			psrc.getPatronRequest().setActiveWorkflow("RET-PUA");
		}

		return Mono.just(psrc);
	}

	private Mono<PatronIdentity> upsertPatronIdentityAtSupplier(RequestWorkflowContext psrc) {
		log.debug("upsertPatronIdentityAtSupplier");

		PatronRequest patronRequest = psrc.getPatronRequest();
		SupplierRequest supplierRequest = psrc.getSupplierRequest();

		return checkIfPatronExistsAtSupplier(psrc)
			.switchIfEmpty(Mono.defer(() -> {
				log.warn("checkIfPatronExistsAtSupplier {} false, creating new patron record", psrc);
				return createPatronAtSupplier(patronRequest, supplierRequest);
			}));
	}

	private Mono<PatronIdentity> checkIfPatronExistsAtSupplier(RequestWorkflowContext psrc) {
		log.debug("checkIfPatronExistsAtSupplier");

		PatronRequest patronRequest = psrc.getPatronRequest();
		SupplierRequest supplierRequest = psrc.getSupplierRequest();
		// log.debug("checkIfPatronExistsAtSupplier req={}, supplierSystemCode={}", patronRequest.getId(), supplierRequest.getHostLmsCode());

		// Get supplier system interface
		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.zipWith(patronService.getUniqueIdStringFor(psrc.getPatron()))
			// Look up virtual patron using generated unique ID string
			.flatMap(tuple -> {
				final var hostlmsclient = tuple.getT1();
				final var uniqueid = tuple.getT2();
				return hostlmsclient.patronAuth("UNIQUE-ID", uniqueid, psrc.getPatronHomeIdentity().getLocalBarcode());
			})
      // Ensure that we have a local patronIdentity record to track the patron in the supplying ILS
			.flatMap(patron -> updateLocalPatronIdentityForLmsPatron(patron, patronRequest, supplierRequest));
	}

	private Mono<PatronIdentity> updateLocalPatronIdentityForLmsPatron(Patron patron, PatronRequest patronRequest, SupplierRequest supplierRequest) {

		String barcodes_as_string = ( ( patron.getLocalBarcodes() != null ) && ( patron.getLocalBarcodes().size() > 0 ) ) ? patron.getLocalBarcodes().toString() : null;

		if ( barcodes_as_string == null ) {
			log.warn("VPatron will have no barcodes {}/{}",patronRequest,patron);
		}

		return checkPatronType( patron.getLocalId().get(0), patron.getLocalPatronType(), patronRequest, supplierRequest.getHostLmsCode())
			.flatMap(function((localId, patronType) ->
                                checkForPatronIdentity(patronRequest, supplierRequest.getHostLmsCode(), localId, patronType, barcodes_as_string)));
	}

	private Mono<Tuple2<String, String>> checkPatronType(String localId, String patronType,
			PatronRequest patronRequest, String supplierHostLmsCode) {

		log.debug("checkPatronType {}, {}", localId, patronType);

		// Work out what the global patronId is we are using for this real patron
		return getRequestingIdentity(patronRequest)
			// Work out the ???
			.flatMap(requestingIdentity -> determinePatronType(supplierHostLmsCode, requestingIdentity))
			// don't continue the stream if the local type matches what we have storred
			.filter(dcbPatronType -> dcbPatronType.equals(patronType))
			// if the the returned value and the storred value were different, we have an empty stream, update the vpatron
			.switchIfEmpty(Mono.defer(() -> updateVirtualPatron(supplierHostLmsCode, localId, patronType)))
			// Construct return tuple
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
		if ((patronRequest != null) &&
			(patronRequest.getRequestingIdentity() != null)) {
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
			.flatMap(function((client,requestingIdentity) -> createVPatronAndSaveIdentity(client,requestingIdentity,patronRequest,hostLmsCode)));
	}

	/**
	 * Create a virtual patron at the supplying library and then store the details of that record in a patron identity record, returning the patronIdentity
	 */
	private Mono<PatronIdentity> createVPatronAndSaveIdentity(
		HostLmsClient client, 
		PatronIdentity requestingIdentity, 
		PatronRequest patronRequest, 
		String hostLmsCode) {

		return createPatronAtSupplier(patronRequest, client, requestingIdentity, hostLmsCode)
			.flatMap(function((localId, patronType) -> checkForPatronIdentity(patronRequest, hostLmsCode, localId, patronType, requestingIdentity.getLocalBarcode())));
	}

	private Mono<Tuple2<String, String>> createPatronAtSupplier(
		PatronRequest patronRequest, HostLmsClient client,
		PatronIdentity requestingPatronIdentity, String supplierHostLmsCode) {
		// Using the patron type from the patrons "Home" patronIdentity, look up what the equivalent patron type is at
		// the supplying system. Then create a patron in the supplying system using that type value.

		log.debug("createPatronAtSupplier2");

		// Patrons can have multiple barcodes. To keep the domain model sane(ish) we store [b1, b2, b3] (As the result of Objects.toString()
		// in the field. Here we unpack that structure back into an array of barcodes that the HostLMS can do with as it pleases
		final List<String> patron_barcodes = (requestingPatronIdentity.getLocalBarcode()!=null) ?
			Arrays.asList(requestingPatronIdentity.getLocalBarcode()
				.substring(1, requestingPatronIdentity.getLocalBarcode().length() - 1).split(", ")):null;

		if ( ( patron_barcodes == null ) || ( patron_barcodes.size() == 0 ) ) {
			log.warn("Virtual patron has no barcodes. Source identity {}. Will be unable to check out to this patron",
				requestingPatronIdentity);
		}

		return determinePatronType(supplierHostLmsCode, requestingPatronIdentity)
			.zipWith( determineUniqueId(patronRequest.getPatron()) )
			.flatMap(tuple -> {
				final var patronType = tuple.getT1();
				final var uniqueId = tuple.getT2();
				return client.createPatron(
						Patron.builder()
							.localBarcodes( patron_barcodes )
							.localPatronType( patronType )
							.uniqueIds( stringToList(uniqueId) )
							.build())
					.map(createdPatron -> Tuples.of(createdPatron, patronType));
			});
	}

	private Mono<String> determineUniqueId(org.olf.dcb.core.model.Patron patron) {
		return patronService.getUniqueIdStringFor(patron);
	}

	private List<String> stringToList(String string) {
		log.debug("stringToList {}",string);
		return string!=null ? List.of(string):null;
	}

	private Mono<PatronIdentity> checkForPatronIdentity(PatronRequest patronRequest,
		String hostLmsCode, String localId, String localPType, String barcode) {

		return patronService.checkForPatronIdentity(patronRequest.getPatron(),
			hostLmsCode, localId, localPType, barcode);
	}

	private Mono<String> determinePatronType(String supplyingHostLmsCode, PatronIdentity requestingIdentity) {

		log.debug("determinePatronType");

		if (supplyingHostLmsCode == null || requestingIdentity == null || requestingIdentity.getHostLms() == null ||
			requestingIdentity.getHostLms().getCode() == null) {

			throw new RuntimeException("Missing patron data - unable to determine patron type at supplier:" + supplyingHostLmsCode);
		}

		// log.debug("determinePatronType {} {} {} requesting identity present",supplyingHostLmsCode, requestingIdentity.getHostLms().getCode(),
		//	requestingIdentity.getLocalPtype());

		// We need to look up the requestingHostLmsCode and not pass supplyingHostLmsCode
		return patronTypeService.determinePatronType(supplyingHostLmsCode,
			requestingIdentity.getHostLms().getCode(),
			requestingIdentity.getLocalPtype(), requestingIdentity.getLocalId());
	}

	public Mono<HostLmsHold> getHold(String hostLmsCode, String holdId) {
		log.debug("getHold({},{})",hostLmsCode,holdId);
		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap( client -> client.getHold(holdId) );
	}
}

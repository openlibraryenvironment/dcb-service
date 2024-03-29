package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.CollectionUtils.isNotEmpty;
import static reactor.function.TupleUtils.function;
import static services.k_int.utils.StringUtils.parseList;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.model.NoHomeIdentityException;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;


@Slf4j
@Prototype
public class SupplyingAgencyService {
	private static final URI ERR0010 = URI.create(
		"https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/2738356304/0010+-+Error+in+place+request+at+supplier");

	private final HostLmsService hostLmsService;
	private final SupplierRequestService supplierRequestService;
	private final PatronService patronService;
	private final PatronTypeService patronTypeService;
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;

	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	public SupplyingAgencyService(HostLmsService hostLmsService,
		SupplierRequestService supplierRequestService, PatronService patronService,
		PatronTypeService patronTypeService,
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
			.zipWhen(psrc -> performSupplierPreflight(psrc) )
			.flatMap(TupleUtils.function((psrc, preflightResult) -> reactToPreflight(preflightResult, psrc) ) )
      // We do this work a level up at PlacePatronRequestAtSupplyingAgencyStateTransition.createAuditEntry
      // commenting out as of 2023-08-16. If audit log looks good will remove entirely.
      .transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest));
	}

	public Mono<Boolean> performSupplierPreflight(RequestWorkflowContext psrc) {

		SupplierRequest supplierRequest = psrc.getSupplierRequest();

		// We need to be sure that we are able to map the canonical patron type to something this supplier can understand
		// We also would like to know that we can map the local item type at the supplier to our canonical values
		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap( client -> client.supplierPreflight(
				psrc.getPatronAgencyCode(),
				psrc.getLenderAgencyCode(),
				psrc.getSupplierRequest().getLocalItemType(),
				psrc.getPatronRequest().getRequestingIdentity().getCanonicalPtype()));
	}

	public Mono<PatronRequest> reactToPreflight(Boolean preflightResult, RequestWorkflowContext psrc) {
		return checkAndCreatePatronAtSupplier(psrc)
	    .flatMap(this::placeRequestAtSupplier)
      .flatMap(this::setPatronRequestWorkflow)
      .flatMap(this::updateSupplierRequest)
      .map(PatronRequest::placedAtSupplyingAgency);
	}

	public Mono<PatronRequest> cleanUp(PatronRequest patronRequest) {
		log.info("WORKFLOW cleanup {}",patronRequest);
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
			(psrc.getPickupAgencyCode() == null)) {

			throw new RuntimeException("Invalid RequestWorkflowContext " + psrc);
		}

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())

			// When placing a bib level hold the item that gets selected MAY NOT be the item DCB thought it was asking for from that
			// provider
			.flatMap(client -> this.placeHoldRequest(client, psrc))
			// ToDo: add a function to look up the item requested and extract the barcode and set it in the LocalRequest so it can be returned in the line below
			.map(lr -> supplierRequest.placed(lr.getLocalId(), lr.getLocalStatus(), lr.getRequestedItemId(), lr.getRequestedItemBarcode()))
			.thenReturn(psrc)
			.onErrorResume(error -> {
				log.error("Error in placeRequestAtSupplier {} : {}", psrc, error.getMessage());

				return Mono.error(unableToPlaceRequestAtSupplyingAgencyProblem(psrc, error,
					patronRequest, patronIdentityAtSupplier, supplierRequest));
			});
	}

	private Mono<LocalRequest> placeHoldRequest(HostLmsClient client,
		RequestWorkflowContext context) {

		log.debug("placeHoldRequest");

		final var patronRequest = context.getPatronRequest();
		final var supplierRequest = context.getSupplierRequest();
		final var patronIdentityAtSupplier = context.getPatronVirtualIdentity();

		final var patron = patronRequest.getPatron();

		final var homeIdentity = patron.getHomeIdentity()
			.orElseThrow(() -> new NoHomeIdentityException(patron.getId(),
				patron.getPatronIdentities()));

		// String note = "Consortial Hold. tno=" + patronRequest.getId();
		String note = context.generateTransactionNote();

		// The patron type and barcode are needed by FOLIO
		// due to how edge-dcb creates a virtual patron on DCB's behalf
		// Have to use the values from the home identity as cannot trust
		// that the values on the virtual identity are correct when they get here
		return determinePatronType(client.getHostLmsCode(), homeIdentity)
			.flatMap(patronTypeAtSupplyingAgency -> client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localPatronId(patronIdentityAtSupplier.getLocalId())
					.localPatronType(patronTypeAtSupplyingAgency)
					.localPatronBarcode(homeIdentity.getLocalBarcode())
					.localBibId(supplierRequest.getLocalBibId())
					// FOLIO needs both the ID and barcode to cross-check the item identity
					// SIERRA when placing a BIB hold - the ITEM that gets held may not be the one we selected
					.localItemId(supplierRequest.getLocalItemId())
					.localItemBarcode(supplierRequest.getLocalItemBarcode())
					// Have to pass both because Sierra and Polaris still use code only
					.pickupLocationCode(context.getPickupAgencyCode())
					.pickupAgency(context.getPickupAgency())
					.note(note)
					.patronRequestId(patronRequest.getId().toString())
					// It is common in III systems to want the pickup location at the supplying library
					// to be set to the location where the item currently resides.
					.supplyingLocalItemLocation(supplierRequest.getLocalItemLocationCode())
					.build()));
	}

	private static ThrowableProblem unableToPlaceRequestAtSupplyingAgencyProblem(
		RequestWorkflowContext context, Throwable error, PatronRequest patronRequest,
		PatronIdentity patronIdentityAtSupplier, SupplierRequest supplierRequest) {

		var builder = Problem.builder()
			.withType(ERR0010)
			.withTitle(
				"Unable to place SUPPLIER hold request for pr=" + patronRequest.getId() + " Lpatron=" + patronIdentityAtSupplier.getLocalId() +
					" Litemid=" + supplierRequest.getLocalItemId() + " Lit=" + supplierRequest.getLocalItemType() + " Lpt=" + patronIdentityAtSupplier.getLocalPtype() + " system=" + supplierRequest.getHostLmsCode())
			.withDetail(error.getMessage())
			// .with("dcbContext", context)
			.with("supplier-dcbPatronId", patronIdentityAtSupplier.getLocalId())
			.with("supplier-dcbLocalItemId", supplierRequest.getLocalItemId())
			.with("supplier-dcbLocalItemBarcode", supplierRequest.getLocalItemBarcode())
			.with("supplier-dcbLocalItemType", supplierRequest.getLocalItemType())
			.with("supplier-dcbLocalPatronType", patronIdentityAtSupplier.getLocalPtype())
			.with("supplier-dcbCanonicalPatronType", patronIdentityAtSupplier.getCanonicalPtype())
			.with("supplier-dcbLocalPatronBarcode", patronIdentityAtSupplier.getLocalBarcode());

		// Pass on any parameters from an underlying problem
		if (error instanceof ThrowableProblem underlyingProblem) {
			if (isNotEmpty(underlyingProblem.getParameters())) {
				underlyingProblem.getParameters().forEach(builder::with);
			}
		}

		return builder.build();
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
				log.warn("checkIfPatronExistsAtSupplier is false, creating new patron record prid={}",patronRequest.getId());
				return createPatronAtSupplier(patronRequest, supplierRequest);
			}));
	}

	private Mono<PatronIdentity> checkIfPatronExistsAtSupplier(RequestWorkflowContext psrc) {

		final var patronRequest = psrc.getPatronRequest();
		final var supplierRequest = psrc.getSupplierRequest();

		log.debug("checkIfPatronExistsAtSupplier(prid={})", patronRequest.getId());

		// Get supplier system interface
		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.findVirtualPatron(psrc.getPatron()))
      // Ensure that we have a local patronIdentity record to track the patron in the supplying ILS
			.flatMap(patron -> updateLocalPatronIdentityForLmsPatron(patron, patronRequest, supplierRequest));
	}

	private Mono<PatronIdentity> updateLocalPatronIdentityForLmsPatron(
		Patron patron, PatronRequest patronRequest, SupplierRequest supplierRequest) {

		String barcodes_as_string = ((patron.getLocalBarcodes() != null)
				&& (patron.getLocalBarcodes().size() > 0))
			? patron.getLocalBarcodes().toString()
			: null;

		if (barcodes_as_string == null) {
			log.warn("VPatron will have no barcodes {}/{}",patronRequest,patron);
		}

		return checkPatronType( patron.getLocalId().get(0), patron.getLocalPatronType(), patronRequest, supplierRequest.getHostLmsCode())
			.flatMap(function((localId, patronType) -> checkForPatronIdentity(patronRequest, supplierRequest.getHostLmsCode(), localId, patronType, barcodes_as_string)));
	}

	private Mono<Tuple2<String, String>> checkPatronType(String localId,
		String patronType, PatronRequest patronRequest, String supplierHostLmsCode) {

		log.debug("checkPatronType localId={}, patronType={}", localId, patronType);

		// Work out what the global patronId is we are using for this real patron
		return getRequestingIdentity(patronRequest)
			// Work out the ???
			.flatMap(requestingIdentity -> determinePatronType(supplierHostLmsCode, requestingIdentity))
			.doOnNext(newlyMappedVPatronType -> log.debug("Testing to see if patron type needs to be updated from {} to {}",patronType,newlyMappedVPatronType) )
			.flatMap(newlyMappedVPatronType -> {

				// if the returned value and the stored value were different, update the virtual patron
				if (newlyMappedVPatronType != patronType) {
					return updateVirtualPatron(supplierHostLmsCode, localId, newlyMappedVPatronType);
				}

				// do nothing if the patron types are equal
				return Mono.just(newlyMappedVPatronType);
			})
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

	private Mono<PatronIdentity> createPatronAtSupplier(
		PatronRequest patronRequest, SupplierRequest supplierRequest) {

		final var hostLmsCode = supplierRequest.getHostLmsCode();

		log.debug("createPatronAtSupplier prid={}, srid={} supplierCode={}",
			patronRequest.getId(), supplierRequest.getId(),hostLmsCode);

		return hostLmsService.getClientFor(hostLmsCode)
			.zipWhen(client -> getRequestingIdentity(patronRequest), Tuples::of)
			.flatMap(function((client,requestingIdentity) ->
				createVPatronAndSaveIdentity(client, requestingIdentity,patronRequest,hostLmsCode,supplierRequest)));
	}

	/**
	 * Create a virtual patron at the supplying library and then store the details of that record in a patron identity record, returning the patronIdentity
	 */
	private Mono<PatronIdentity> createVPatronAndSaveIdentity(
		HostLmsClient client, 
		PatronIdentity requestingIdentity, 
		PatronRequest patronRequest, 
		String hostLmsCode,
		SupplierRequest supplierRequest) {

		return createPatronAtSupplier(patronRequest, client, requestingIdentity, hostLmsCode, supplierRequest)
			.flatMap(function((localId, patronType) -> checkForPatronIdentity(patronRequest, hostLmsCode, localId, patronType, requestingIdentity.getLocalBarcode())));
	}

	private Mono<Tuple2<String, String>> createPatronAtSupplier(
		PatronRequest patronRequest, HostLmsClient client,
		PatronIdentity requestingPatronIdentity, String supplierHostLmsCode,
		SupplierRequest supplierRequest) {
		// Using the patron type from the patrons "Home" patronIdentity, look up what the equivalent patron type is at
		// the supplying system. Then create a patron in the supplying system using that type value.

		log.debug("createPatronAtSupplier2");

		// Patrons can have multiple barcodes. To keep the domain model sane(ish) we store [b1, b2, b3] (As the result of Objects.toString()
		// in the field. Here we unpack that structure back into an array of barcodes that the HostLMS can do with as it pleases
		final List<String> patron_barcodes = parseList( requestingPatronIdentity.getLocalBarcode() );

		if ((patron_barcodes == null) || (patron_barcodes.size() == 0)) {
			log.warn("Virtual patron has no barcodes. Source identity {}. Will be unable to check out to this patron",
				requestingPatronIdentity);
		}

		return determinePatronType(supplierHostLmsCode, requestingPatronIdentity)
			.zipWith(Mono.just(patronRequest.getPatron()))
			.flatMap(tuple -> {
				final var patronType = tuple.getT1();
				final var uniqueId = tuple.getT2().determineUniqueId();
				return client.createPatron(
					Patron.builder()
						.localBarcodes(patron_barcodes)
						.localPatronType(patronType)
						.uniqueIds(stringToList(uniqueId))
						.localHomeLibraryCode(requestingPatronIdentity.getLocalHomeLibraryCode())
						.localItemId(supplierRequest.getLocalItemId())
						.build())
					.map(createdPatronId -> Tuples.of(createdPatronId, patronType))
					.doOnSuccess( t -> log.debug("determinePatronType ended with success {}",t) );
			});
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

	private Mono<String> determinePatronType(String supplyingHostLmsCode,
		PatronIdentity requestingIdentity) {

		log.debug("determinePatronType");

		if (supplyingHostLmsCode == null || requestingIdentity == null
			|| requestingIdentity.getHostLms() == null || requestingIdentity.getHostLms().getCode() == null) {

			throw new RuntimeException("Missing patron data - unable to determine patron type at supplier:" + supplyingHostLmsCode);
		}

		// log.debug("determinePatronType {} {} {} requesting identity present",supplyingHostLmsCode, requestingIdentity.getHostLms().getCode(),
		//	requestingIdentity.getLocalPtype());

		// We need to look up the requestingHostLmsCode and not pass supplyingHostLmsCode
		return patronTypeService.determinePatronType(supplyingHostLmsCode,
			requestingIdentity.getHostLms().getCode(),
			requestingIdentity.getLocalPtype(), requestingIdentity.getLocalId());
	}

	public Mono<HostLmsRequest> getRequest(String hostLmsCode, String localRequestId) {
		log.debug("getHold({}, {})", hostLmsCode, localRequestId);

		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap(client -> client.getRequest(localRequestId));
	}
}

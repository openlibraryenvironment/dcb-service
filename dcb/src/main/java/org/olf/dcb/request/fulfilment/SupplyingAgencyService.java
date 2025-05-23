package org.olf.dcb.request.fulfilment;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.model.*;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.AgencyRepository;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.net.URI;
import java.util.*;
import java.util.function.Function;

import static io.micronaut.core.util.CollectionUtils.isNotEmpty;
import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;
import static services.k_int.utils.StringUtils.parseList;


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
	private final PatronRequestAuditService patronRequestAuditService;
	private final AgencyRepository agencyRepository;

	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	public SupplyingAgencyService(HostLmsService hostLmsService,
		SupplierRequestService supplierRequestService, PatronService patronService,
		PatronTypeService patronTypeService,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
		RequestWorkflowContextHelper requestWorkflowContextHelper,
		PatronRequestAuditService patronRequestAuditService, AgencyRepository agencyRepository) {

		this.hostLmsService = hostLmsService;
		this.supplierRequestService = supplierRequestService;
		this.patronService = patronService;
		this.patronTypeService = patronTypeService;
		this.agencyRepository = agencyRepository;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.patronRequestAuditService = patronRequestAuditService;
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
			.flatMap(TupleUtils.function((psrc, preflightResult) -> reactToPreflight(preflightResult, psrc) ) );
      // We do this work a level up at PlacePatronRequestAtSupplyingAgencyStateTransition.createAuditEntry
      // commenting out as of 2023-08-16. If audit log looks good will remove entirely.;
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
      .flatMap(this::updateSupplierRequest)
      .map(PatronRequest::placedAtSupplyingAgency);
	}

	private enum HoldOperation {
		DELETE("CLEAN UP"),
		CANCEL("CANCEL");

		private final String description;

		HoldOperation(String description) {
			this.description = description;
		}

		public String getDescription() {
			return description;
		}
	}

	private Mono<RequestWorkflowContext> handleSupplierHoldOperation(
		RequestWorkflowContext context,
		HoldOperation operation) {

		final var supplierRequest = getValueOrNull(context, RequestWorkflowContext::getSupplierRequest);
		final var hostLmsCode = getValueOrNull(supplierRequest, SupplierRequest::getHostLmsCode);
		final var localRequestId = getValueOrNull(supplierRequest, SupplierRequest::getLocalId);

		String operationDescription = operation.getDescription();
		log.info("WORKFLOW attempting to {} local supplier hold :: {}", operationDescription, supplierRequest);

		if (hostLmsCode == null || localRequestId == null) {
			final var patronRequest = getValueOrNull(context, RequestWorkflowContext::getPatronRequest);

			log.error("WORKFLOW could not {} supplier hold :: hostLmsCode={} localRequestId={}",
				operationDescription, hostLmsCode, localRequestId);

			final var message = operationDescription + " supplier hold : Skipped";
			final var auditData = new HashMap<String, Object>();
			auditData.put("WORKFLOW could not " + operationDescription + " supplier hold because a required value was null", Map.of(
				"hostLmsCode", getValue(hostLmsCode, "No value present"),
				"localRequestId", getValue(localRequestId, "No value present")
			));

			return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
				.flatMap(audit -> Mono.just(context));
		}

		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap(client -> checkHoldExists(client, localRequestId, context))
			.flatMap(client -> {
				switch (operation) {
					case DELETE:
						return client.deleteHold(localRequestId);
					case CANCEL:
						// For cancel operation, we need additional parameters
						final var localItemId = getValueOrNull(supplierRequest, SupplierRequest::getLocalItemId);
						final var virtualPatronLocalID = getValueOrNull(supplierRequest, SupplierRequest::getVirtualIdentity, PatronIdentity::getLocalId);

						return client.cancelHoldRequest(CancelHoldRequestParameters.builder()
							.localRequestId(localRequestId)
							.localItemId(localItemId)
							.patronId(virtualPatronLocalID)
							.build());
					default:
						return Mono.error(new IllegalArgumentException("Unsupported operation: " + operation));
				}
			})
			// Catch any skipped operations
			.switchIfEmpty(Mono.defer(() -> Mono.just("OK")))
			// Genuine error we didn't account for
			.onErrorResume(logAndReturnErrorString(context))
			.thenReturn(context);
	}

	public Mono<RequestWorkflowContext> cleanUp(RequestWorkflowContext context) {
		return handleSupplierHoldOperation(context, HoldOperation.DELETE);
	}

	public Mono<RequestWorkflowContext> cancelHold(RequestWorkflowContext context) {
		return handleSupplierHoldOperation(context, HoldOperation.CANCEL);
	}

	private Mono<HostLmsClient> checkHoldExists(
		HostLmsClient client, String localRequestId, RequestWorkflowContext context) {

		final var patronRequest = getValueOrNull(context, RequestWorkflowContext::getPatronRequest);
		final var supplierRequest = getValueOrNull(context, RequestWorkflowContext::getSupplierRequest);
		final var supplierPatronId = getValueOrNull(supplierRequest, SupplierRequest::getVirtualIdentity, PatronIdentity::getLocalId);
		final var hostlmsRequest = HostLmsRequest.builder().localId(localRequestId).localPatronId(supplierPatronId).build();

		return client.getRequest(hostlmsRequest)
			.flatMap(hostLmsRequest -> {

				// if the hold exists a local id will be present
				if (hostLmsRequest != null && hostLmsRequest.getLocalId() != null) {

					// return the client to proceed with deletion
					return Mono.just(client);
				}

				// no local id to delete, skip delete by passing back an empty
				final var message = "Delete supplier hold : Skipped";
				final var auditData = new HashMap<String, Object>();
				auditData.put("hold", hostLmsRequest);
				return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData).flatMap(audit -> Mono.empty());
			})
			.onErrorResume(error -> {

				// we encountered an error when confirming the hold exists
				final var message = "Delete supplier hold : Skipped";
				final var auditData = new HashMap<String, Object>();
				auditThrowable(auditData, "Throwable", error);
				return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData).flatMap(audit -> Mono.empty());
			});
	}

	private Function<Throwable, Mono<String>> logAndReturnErrorString(RequestWorkflowContext requestWorkflowContext) {

		final var patronRequest = getValueOrNull(requestWorkflowContext, RequestWorkflowContext::getPatronRequest);

		return error -> {
			final var message = "Delete supplier hold : Failed";
			final var auditData = new HashMap<String, Object>();
			auditThrowable(auditData, "Throwable", error);
			return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
				.flatMap(audit -> Mono.just("Error"));
		};
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
			.map(lr -> supplierRequest.placed(
				lr.getLocalId(), lr.getLocalStatus(),
				lr.getRawLocalStatus(), lr.getRequestedItemId(),
				lr.getRequestedItemBarcode()))
			.thenReturn(psrc)
			.onErrorResume(error -> {
				log.error("Error in placeRequestAtSupplier {} : {}", psrc, error.getMessage());

				return Mono.error(unableToPlaceRequestAtSupplyingAgencyProblem(error,
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

		String dcb_default_pickup_note = context.generatePickupNote();

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
					.localHoldingId(getValueOrNull(supplierRequest, SupplierRequest::getLocalHoldingId))
					.localItemBarcode(supplierRequest.getLocalItemBarcode())
					// Have to pass both because Sierra and Polaris still use code only
					.pickupLocationCode(context.getPickupAgencyCode())
					.pickupLocation(context.getPickupLocation())
					.pickupAgency(context.getPickupAgency())
					.pickupLibrary(context.getPickupLibrary())
					.pickupNote(dcb_default_pickup_note)
					.note(note)
					.patronRequestId(patronRequest.getId().toString())
					// It is common in III systems to want the pickup location at the supplying library
					// to be set to the location where the item currently resides.
					.supplyingLocalItemLocation(supplierRequest.getLocalItemLocationCode())
					.build()));
	}

	private static ThrowableProblem unableToPlaceRequestAtSupplyingAgencyProblem(
		Throwable error, PatronRequest patronRequest, PatronIdentity patronIdentityAtSupplier,
		SupplierRequest supplierRequest) {

		var builder = Problem.builder()
			.withType(ERR0010)
			.withTitle(
				"Unable to place SUPPLIER hold request for pr=" + patronRequest.getId() + " Lpatron=" + patronIdentityAtSupplier.getLocalId() +
					" Litemid=" + supplierRequest.getLocalItemId() + " Lit=" + supplierRequest.getLocalItemType() + " Lpt=" + patronIdentityAtSupplier.getLocalPtype() + " system=" + supplierRequest.getHostLmsCode())
			.withDetail(error.getMessage())
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

	private Mono<PatronIdentity> upsertPatronIdentityAtSupplier(RequestWorkflowContext psrc) {
		log.debug("upsertPatronIdentityAtSupplier");

		PatronRequest patronRequest = psrc.getPatronRequest();
		SupplierRequest supplierRequest = psrc.getSupplierRequest();

		return checkIfPatronExistsAtSupplier(psrc)
			// VirtualPatronNotFound will trigger createPatronAtSupplier
			.onErrorResume(VirtualPatronNotFound.class, handleVirtualPatronNotFound(patronRequest, supplierRequest))
			.onErrorResume(MultipleVirtualPatronsFound.class, handleMultipleVirtualPatronsFound(patronRequest, supplierRequest));
	}

	private Mono<PatronIdentity> checkIfPatronExistsAtSupplier(RequestWorkflowContext psrc) {

		final var patronRequest = psrc.getPatronRequest();
		final var supplierRequest = psrc.getSupplierRequest();

		log.debug("checkIfPatronExistsAtSupplier(prid={})", patronRequest.getId());

		// Get supplier system interface
		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.findVirtualPatron(psrc.getPatron()))
      // Ensure that we have a local patronIdentity record to track the patron in the supplying ILS
			.flatMap(patron -> updateLocalPatronIdentityForLmsPatron(patron, patronRequest, supplierRequest))
			.flatMap( auditVirtualPatron(patronRequest, "Virtual patron : found") );
	}

	private Function<VirtualPatronNotFound, Mono<PatronIdentity>> handleVirtualPatronNotFound(
		PatronRequest patronRequest, SupplierRequest supplierRequest) {

		return error -> patronRequestAuditService.auditThrowableAbstractProblem(patronRequest, "Virtual patron : not found", error)
			.doOnSuccess(__ -> log.warn("checkIfPatronExistsAtSupplier is false, creating new patron record prid={}",patronRequest.getId()))
			.flatMap(audit -> createPatronAtSupplier(patronRequest, supplierRequest));
	}

	private Function<MultipleVirtualPatronsFound, Mono<PatronIdentity>> handleMultipleVirtualPatronsFound(
		PatronRequest patronRequest, SupplierRequest supplierRequest) {

		return error -> patronRequestAuditService.auditThrowableAbstractProblem(patronRequest, "Virtual patron : multiple found", error)
			.doOnSuccess(__ -> log.warn("Multiple virtual patrons found at supplier={}",supplierRequest.getHostLmsCode()))
			.flatMap(audit -> Mono.error(error));
	}

	private Mono<PatronIdentity> updateLocalPatronIdentityForLmsPatron(
		Patron patron, PatronRequest patronRequest, SupplierRequest supplierRequest) {

		log.debug("updateLocalPatronIdentityForLmsPatron {}",patron);

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

		log.debug("checkPatronType id of patron at home system={}, type of patron at home system={} supplyingSystem={}", localId, patronType, supplierHostLmsCode);

		// Work out what the global patronId is we are using for this real patron
		return getRequestingIdentity(patronRequest)
			.doOnNext(requestingIdentity -> log.debug("checkPatronType checking requesting identity {} at {}",requestingIdentity, supplierHostLmsCode) )
			// Work out the ???
			.flatMap(requestingIdentity -> determinePatronType(supplierHostLmsCode, requestingIdentity))
			.doOnNext(newlyMappedVPatronType -> log.debug("Testing to see if patron type needs to be updated from {} to {}",patronType,newlyMappedVPatronType) )
			.flatMap(newlyMappedVPatronType -> {

				if (patronType == null || newlyMappedVPatronType == null) {
					throw new NullPointerException("One or both values are null: " +
						"known patron type = " + patronType + ", " +
						"determined patron type = " + newlyMappedVPatronType);
				}

				// if the returned value and the stored value were different, update the virtual patron
				else if (!Objects.equals(newlyMappedVPatronType, patronType)) {
					return updateVirtualPatron(supplierHostLmsCode, localId, newlyMappedVPatronType)

						// check the patron type was infact updated by checking what was returned
						.flatMap(returnedPatron -> {

							final var returnedLocalPatronType = returnedPatron.getLocalPatronType();

							if (!returnedLocalPatronType.equals(newlyMappedVPatronType)) { // then unsuccessful update

								var auditData = new HashMap<String, Object>();
								auditData.put("supplier-dcbPatronId", localId);
								auditData.put("supplier-hostLmsCode", supplierHostLmsCode);
								auditData.put("supplier-dcbLocalPatronType-before-update", patronType);
								auditData.put("supplier-dcbLocalPatronType-after-update", returnedLocalPatronType);
								auditData.put("supplier-dcbLocalPatronType-desired-update", newlyMappedVPatronType);
								auditData.put("supplier-patron-returned-from-update", returnedPatron.toString());

								final var auditMessage = String.format("Patron update failed : localId %s ptype %s hostlms %s",
								localId, newlyMappedVPatronType, supplierHostLmsCode);

								return patronRequestAuditService
									.addAuditEntry(patronRequest, auditMessage, auditData)
									.map(audit -> returnedLocalPatronType);
							}

							// successful update
							return Mono.just(returnedLocalPatronType);
						});
				}

				// do nothing if the patron types are equal
				return Mono.just(newlyMappedVPatronType);
			})
			// Construct return tuple
			.map(updatedPatronType -> Tuples.of(localId, updatedPatronType));
	}

	private Mono<Patron> updateVirtualPatron(String supplierHostLmsCode, String localId, String patronType) {
		log.debug("updateVirtualPatron {}, {}", localId, patronType);

		return hostLmsService.getClientFor(supplierHostLmsCode)
			.flatMap(hostLmsClient -> hostLmsClient.updatePatron(localId, patronType));
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
			.flatMap(function((localId, patronType) -> checkForPatronIdentity(patronRequest, hostLmsCode, localId, patronType, requestingIdentity.getLocalBarcode())))
			.flatMap( auditVirtualPatron(patronRequest, "Virtual patron : created") );
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
				final var homeIdentityLocalId = getValueOrNull(requestingPatronIdentity, PatronIdentity::getLocalId);
				return client.createPatron(
					Patron.builder()
						.localId(Collections.singletonList(homeIdentityLocalId))
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

	public Mono<HostLmsRequest> getRequest(String hostLmsCode, HostLmsRequest request) {

		final var localRequestId = getValueOrNull(request, HostLmsRequest::getLocalId);

		log.debug("getHold({}, {})", hostLmsCode, localRequestId);

		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap(client -> client.getRequest(request));
	}

	public Mono<Patron> getPatron(RequestWorkflowContext ctx) {

		final var supplierRequest = ctx.getSupplierRequest();
		final var virtualIdentity = ctx.getPatronVirtualIdentity();

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.getPatronByLocalId(virtualIdentity.getLocalId()));
	}

	private Function<PatronIdentity, Mono<PatronIdentity>> auditVirtualPatron(PatronRequest patronRequest, String message) {
		return patronIdentity -> {

			final var auditData = new HashMap<String, Object>();

			var virtualPatron =  VirtualPatron.builder()
				.dcbPatronIdentityID(patronIdentity.getId().toString())
				.localSystemId(patronIdentity.getLocalId());

			try { // adding unique id to the audit data
				var determineUniqueId = patronIdentity.getPatron().determineUniqueId();
				virtualPatron.determinedUniqueId(determineUniqueId);
			} catch (Exception e) {
				log.error("Failed to add unique id to virtual patron audit", e);
				virtualPatron.determinedUniqueId(e.toString());
			}

			auditData.put("virtualPatron", virtualPatron.build());

			return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
				.thenReturn(patronIdentity);
		};
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class VirtualPatron {
		@Nullable
		private String localSystemId;
		@Nullable
		private String dcbPatronIdentityID;
		@Nullable
		private String determinedUniqueId;
	}
}

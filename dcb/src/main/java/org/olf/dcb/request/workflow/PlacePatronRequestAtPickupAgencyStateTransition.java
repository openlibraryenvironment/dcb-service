package org.olf.dcb.request.workflow;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.model.*;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.request.fulfilment.*;
import org.olf.dcb.request.resolution.SharedIndexService;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.*;
import java.util.function.Function;

import static io.micronaut.core.util.CollectionUtils.isNotEmpty;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;
import static services.k_int.utils.StringUtils.parseList;

@Slf4j
@Prototype
public class PlacePatronRequestAtPickupAgencyStateTransition implements PatronRequestStateTransition {

	private final PatronRequestAuditService patronRequestAuditService;
	private final SharedIndexService sharedIndexService;
	private final AgencyService agencyService;
	private final HostLmsService hostLmsService;
	private final PatronTypeService patronTypeService;
	private final PatronService patronService;

	private static final List<Status> possibleSourceStatus = List.of(Status.REQUEST_PLACED_AT_BORROWING_AGENCY);

	public PlacePatronRequestAtPickupAgencyStateTransition(
		PatronRequestAuditService patronRequestAuditService,
		SharedIndexService sharedIndexService,
		AgencyService agencyService,
		HostLmsService hostLmsService,
		PatronTypeService patronTypeService,
		PatronService patronService)
	{
		this.patronRequestAuditService = patronRequestAuditService;
		this.sharedIndexService = sharedIndexService;
		this.agencyService = agencyService;
		this.hostLmsService = hostLmsService;
		this.patronTypeService = patronTypeService;
		this.patronService = patronService;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {

		log.debug("PlacePatronRequestAtPickupAgencyStateTransition firing for {}", ctx.getPatronRequest());

		final var patronRequest = getValueOrNull(ctx, RequestWorkflowContext::getPatronRequest);
		final var resolutionCount = getValueOrNull(patronRequest, PatronRequest::getResolutionCount);

		if (resolutionCount != null && resolutionCount > 1) {
			log.debug("re-resolution: update existing pickup request");
			
			return updateExistingPickupRequest(ctx);
		}

		return placeRequestAtPickupAgencyWorkflow(ctx);
	}

	private Mono<RequestWorkflowContext> placeRequestAtPickupAgencyWorkflow(RequestWorkflowContext ctx) {
		return createPickupBib(ctx)
			.flatMap(this::createPickupItem)
			.flatMap(this::checkAndCreatePatronAtPickupAgency)
			.flatMap(this::placeRequestAtPickupAgency)
			.doOnSuccess(pr -> {
				log.debug("Placed patron request at pickup agency: pr={}", pr);
				ctx.getWorkflowMessages().add("Placed patron request at pickup agency");
			})
			.doOnError(error -> {
				log.error("Error occurred during placing a patron request at pickup agency: {}", error.getMessage());
				ctx.getWorkflowMessages().add("Error occurred during placing a patron request at pickup agency: "+error.getMessage());
			})
			.thenReturn(ctx);
	}

	private Mono<RequestWorkflowContext> updateExistingPickupRequest(RequestWorkflowContext ctx) {

		final var patronRequest = ctx.getPatronRequest();
		final var supplierRequest = ctx.getSupplierRequest();
		final var hostLmsClient = ctx.getPickupSystem();

		return getSupplyingAgencyCode(supplierRequest)
			.map(supplyingAgencyCode -> LocalRequest.builder()
				// existing request info
				.localId(patronRequest.getPickupRequestId())
				.requestedItemId(patronRequest.getPickupItemId())
				// new supplier info
				.requestedItemBarcode(supplierRequest.getLocalItemBarcode())
				.supplyingAgencyCode(supplyingAgencyCode)
				.supplyingHostLmsCode(supplierRequest.getHostLmsCode())
				.canonicalItemType(supplierRequest.getCanonicalItemType())
				.build())
			.doOnSuccess(localRequest -> log.info("updateExistingPickupRequest({})", localRequest))
			.flatMap(hostLmsClient::updateHoldRequest)
			.map(lr -> patronRequest.placedAtPickupAgency(
				lr.getLocalId(), lr.getLocalStatus(),
				lr.getRawLocalStatus(), lr.getRequestedItemId(),
				lr.getRequestedItemBarcode()))
			.doOnSuccess(pr -> {
				log.info("Updated patron request at pickup agency: {}", pr);
				ctx.getWorkflowMessages().add("Updated patron request at pickup agency");
			})
			.doOnError(error -> {
				log.error("Error occurred during updating a patron request at pickup agency: {}", error.getMessage());
				ctx.getWorkflowMessages().add("Error occurred during updating a patron request at pickup agency: "+error.getMessage());
			})
			.thenReturn(ctx)
			.switchIfEmpty( Mono.defer(() -> Mono.error(new DcbError("Failed to update existing pickup request."))) );
	}

	private Mono<RequestWorkflowContext> createPickupBib(RequestWorkflowContext ctx) {
		final var patronRequest = ctx.getPatronRequest();
		final UUID bibClusterId = patronRequest.getBibClusterId();
		final var hostLmsClient = ctx.getPickupSystem();

		log.info("createPickupBib for cluster {}", bibClusterId);

		if (hostLmsClient == null) {
			log.error("Cannot create a bib item at host system because hostLmsClient is NULL");
			throw new DcbError("Cannot create a bib item at host system because hostLmsClient is NULL");
		}

		return sharedIndexService.findSelectedBib(bibClusterId)
			.map(this::extractBibData)
			.flatMap(bib -> Mono.zip(
				hostLmsClient.createBib(bib).map(patronRequest::setPickupBibId),
				Mono.just(bib.getTitle())
			))
			.map(tuple -> {
				return ctx.setPatronRequest(patronRequest).setPickupBibTitle(tuple.getT2());
			})
			.switchIfEmpty(Mono.error(new DcbError(
				"Failed to create pickup bib at " + hostLmsClient.getHostLmsCode() + " for cluster " + bibClusterId)));
	}

	private Bib extractBibData(BibRecord bibRecord) {
		log.info("extractBibData(bibRecord: {})", bibRecord);

		// Guard clause
		if (bibRecord.getTitle() == null) {
			throw new IllegalArgumentException("Missing title information.");
		}

		return Bib.builder().title(bibRecord.getTitle())
			.author(bibRecord.getAuthor() != null ? bibRecord.getAuthor().getName() : null)
			.build();
	}

	private Mono<RequestWorkflowContext> createPickupItem(RequestWorkflowContext requestWorkflowContext) {

		final var supplierRequest = requestWorkflowContext.getSupplierRequest();

		return getSupplyingAgencyCode(supplierRequest)
			.flatMap(supplyingAgencyCode -> pickupItemRequest(requestWorkflowContext, supplyingAgencyCode));
	}

	private Mono<RequestWorkflowContext> pickupItemRequest(
		RequestWorkflowContext requestWorkflowContext, String supplyingAgencyCode) {

		log.info("pickupItemRequest(...)");

		final var patronRequest = requestWorkflowContext.getPatronRequest();
		final var supplierRequest = requestWorkflowContext.getSupplierRequest();
		final var hostLmsClient = requestWorkflowContext.getPickupSystem();

		final String pickupBibId = patronRequest.getPickupBibId();
		Objects.requireNonNull(pickupBibId, "Pickup bib ID not set on Patron Request");

		log.info("pickupItemRequest for pickupBibId {}/{}", pickupBibId, supplierRequest.getLocalItemLocationCode());
		log.info("slToAgency:{} {} {} {} {}", "Location", supplierRequest.getHostLmsCode(),
			supplierRequest.getLocalItemLocationCode(), "AGENCY", "DCB");
		log.info("localHomeLibraryCode: {}", Optional.ofNullable(requestWorkflowContext.getPickupPatronIdentity())
			.map(PatronIdentity::getLocalHomeLibraryCode)
			.orElse(null));

		final var pickupPatronHomeLocation = getValueOrNull(requestWorkflowContext,
			RequestWorkflowContext::getPickupPatronIdentity, PatronIdentity::getLocalHomeLibraryCode);

		// So far, when creating items, we have used the supplying library code as the location for the item. This is so that
		// the borrowing library knows where to return the item. We pass this as locationCode in the CreateItemCommand.
		// POLARIS however needs the location code to be a real location in the local POLARIS System and expects the location
		// of the item to be the patrons home library (Because there is no PUA currently). A note is used for routing details
		// so the borrowing library knows where to return the item. We don't want to switch system type here - instead we should
		// be passing enough detail at this point for any implementation to have the information it needs to populate the request.
		// patronIdentity.getLocalHomeLibraryCode is added to CreateItemCommand as patronHomeLocation
		return hostLmsClient.createItem(
				new CreateItemCommand(
					patronRequest.getId(),
					pickupBibId,

					// supplier information
					supplyingAgencyCode,
					supplierRequest.getHostLmsCode(),
					supplierRequest.getLocalItemBarcode(),
					supplierRequest.getCanonicalItemType(),

					pickupPatronHomeLocation))
			.doOnSuccess(hostLmsItem -> log.info("Created pickup item: {}", hostLmsItem))
			.map(patronRequest::addPickupItemDetails)
			.map(requestWorkflowContext::setPatronRequest)
			.switchIfEmpty(Mono.defer(() -> Mono.error(new DcbError("Failed to create Pickup item."))));
	}

	private Mono<String> getSupplyingAgencyCode(SupplierRequest supplierRequest) {

		final var agency = getValueOrNull(supplierRequest, SupplierRequest::getResolvedAgency);
		final var agencyUUID = getValueOrNull(agency, Agency::getId);

		log.debug("getSupplyingAgencyCode(agency: {}, agencyUUID: {})", agency, agencyUUID);

		// Check the resolved agency is valid before use
		return Mono.justOrEmpty(agencyUUID)
			.flatMap(agencyService::findById)
			.doOnSuccess(foundAgency -> log.debug("Found valid supplying agency for UUID {}", agencyUUID))
			.map(Agency::getCode)
			.switchIfEmpty(Mono.defer(() -> Mono.error(
				new DcbError("Failed to find valid supplying agency for resolved agency UUID: " + agencyUUID))));
	}

	private Mono<RequestWorkflowContext> placeRequestAtPickupAgency(RequestWorkflowContext psrc) {
		log.debug("placeRequestAtPickupAgency");

		PatronRequest patronRequest = psrc.getPatronRequest();
		SupplierRequest supplierRequest = psrc.getSupplierRequest();
		PatronIdentity patronIdentityAtPickupAgency = psrc.getPickupPatronIdentity();

		// Validate that the context contains all the information we need to execute this step
		if ((patronRequest == null) ||
			(supplierRequest == null) ||
			(patronIdentityAtPickupAgency == null) ||
			(psrc.getPickupAgencyCode() == null)) {

			throw new RuntimeException("Invalid RequestWorkflowContext " + psrc);
		}

		return
			// When placing a bib level hold the item that gets selected MAY NOT be the item DCB thought it was asking for from that
			// provider
			placeHoldRequest(psrc)
			// ToDo: add a function to look up the item requested and extract the barcode and set it in the LocalRequest so it can be returned in the line below
			.map(lr -> patronRequest.placedAtPickupAgency(
				lr.getLocalId(), lr.getLocalStatus(),
				lr.getRawLocalStatus(), lr.getRequestedItemId(),
				lr.getRequestedItemBarcode()))
			.thenReturn(psrc)
			.onErrorResume(error -> {
				log.error("Error in placeRequestAtPickupAgency {} : {}", psrc, error.getMessage());

				return Mono.error(unableToPlaceRequestAtPickupAgencyProblem(error, patronRequest, patronIdentityAtPickupAgency, psrc.getPickupSystemCode()));
			});
	}

	private Mono<LocalRequest> placeHoldRequest(RequestWorkflowContext context) {

		log.debug("placeHoldRequest");

		final var patronRequest = context.getPatronRequest();
		final var supplierRequest = context.getSupplierRequest();
		final var patronIdentityAtPickupAgency = context.getPickupPatronIdentity();
		final var client = context.getPickupSystem();

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
			.flatMap(patronTypeAtPickupAgency -> client.placeHoldRequestAtPickupAgency(
				PlaceHoldRequestParameters.builder()
					.localPatronId(patronIdentityAtPickupAgency.getLocalId())
					.localPatronType(patronTypeAtPickupAgency)
					.localPatronBarcode(homeIdentity.getLocalBarcode())
					.localBibId(patronRequest.getPickupBibId())
					.title(context.getPickupBibTitle())
					// FOLIO needs both the ID and barcode to cross-check the item identity
					// SIERRA when placing a BIB hold - the ITEM that gets held may not be the one we selected
					.localItemId(patronRequest.getPickupItemId())
					.localHoldingId(getValueOrNull(patronRequest, PatronRequest::getPickupHoldingId))

					// TODO: we should pass the pickup item barcode here
					// Have to pass both because Sierra and Polaris still use code only
					.pickupLocationCode(context.getPickupAgencyCode())
					.pickupLocation(context.getPickupLocation())
					.pickupAgency(context.getPickupAgency())
					.note(note)
					.patronRequestId(patronRequest.getId().toString())
					// It is common in III systems to want the pickup location at the supplying library
					// to be set to the location where the item currently resides.
					.supplyingLocalItemId(supplierRequest.getLocalItemId())
					.supplyingLocalItemLocation(supplierRequest.getLocalItemLocationCode())
					.supplyingLocalItemBarcode(supplierRequest.getLocalItemBarcode())
					.supplyingAgencyCode(context.getLenderAgencyCode())
					.canonicalItemType(supplierRequest.getCanonicalItemType())
					.build()));
	}

	private static ThrowableProblem unableToPlaceRequestAtPickupAgencyProblem(
		Throwable error, PatronRequest patronRequest, PatronIdentity patronIdentityAtPickupAgency, String system) {

		var builder = Problem.builder()
			.withTitle(
				"Unable to place PICKUP hold request for pr=" + patronRequest.getId() + " Lpatron=" + patronIdentityAtPickupAgency.getLocalId() +
					" Litemid=" + patronRequest.getPickupItemId() + " Lit=" + patronRequest.getPickupItemType() + " Lpt=" + patronIdentityAtPickupAgency.getLocalPtype() + " system=" + system)
			.withDetail(error.getMessage())
			.with("pickup-dcbPatronId", patronIdentityAtPickupAgency.getLocalId())
			.with("pickup-dcbLocalItemId", patronRequest.getPickupItemId())
			.with("pickup-dcbLocalItemBarcode", patronRequest.getPickupItemBarcode())
			.with("pickup-dcbLocalItemType", patronRequest.getPickupItemType())
			.with("pickup-dcbLocalPatronType", patronIdentityAtPickupAgency.getLocalPtype())
			.with("pickup-dcbCanonicalPatronType", patronIdentityAtPickupAgency.getCanonicalPtype())
			.with("pickup-dcbLocalPatronBarcode", patronIdentityAtPickupAgency.getLocalBarcode());

		// Pass on any parameters from an underlying problem
		if (error instanceof ThrowableProblem underlyingProblem) {
			if (isNotEmpty(underlyingProblem.getParameters())) {
				underlyingProblem.getParameters().forEach(builder::with);
			}
		}

		return builder.build();
	}

	private Mono<RequestWorkflowContext> checkAndCreatePatronAtPickupAgency(RequestWorkflowContext psrc) {
		log.debug("checkAndCreatePatronAtPickupAgency");

		final var patronRequest = psrc.getPatronRequest();

		return upsertPatronIdentityAtPickupAgency(psrc)
			.map(patronIdentity -> {
				psrc.setPickupPatronIdentity(patronIdentity);
				patronRequest.setPickupPatronId(patronIdentity.getLocalId());
				return psrc.setPatronRequest(patronRequest);
			});
	}

	private Mono<PatronIdentity> upsertPatronIdentityAtPickupAgency(RequestWorkflowContext requestWorkflowContext) {
		log.debug("upsertPatronIdentityAtPickupAgency");

		return checkIfPatronExistsAtPickupAgency(requestWorkflowContext)
			// VirtualPatronNotFound will trigger createPatronAtPickupAgency
			.onErrorResume(VirtualPatronNotFound.class, handleVirtualPatronNotFound(requestWorkflowContext))
			.onErrorResume(MultipleVirtualPatronsFound.class, handleMultipleVirtualPatronsFound(requestWorkflowContext));
	}

	private Mono<PatronIdentity> checkIfPatronExistsAtPickupAgency(RequestWorkflowContext psrc) {

		final var patronRequest = psrc.getPatronRequest();
		final var pickupHostlmsCode = psrc.getPickupSystemCode();

		log.debug("checkIfPatronExistsAtPickupAgency(prid={})", patronRequest.getId());

		return hostLmsService.getClientFor(pickupHostlmsCode)
			.flatMap(hostLmsClient -> hostLmsClient.findVirtualPatron(psrc.getPatron()))
			// Ensure that we have a local patronIdentity record to track the patron in the pickup ILS
			.flatMap(patron -> updateLocalPatronIdentityForLmsPatron(patron, patronRequest, pickupHostlmsCode))
			.flatMap( auditPickupPatron(patronRequest, "Pickup patron : found") );
	}

	private Function<VirtualPatronNotFound, Mono<PatronIdentity>> handleVirtualPatronNotFound(RequestWorkflowContext requestWorkflowContext) {

		final var patronRequest = requestWorkflowContext.getPatronRequest();

		return error -> patronRequestAuditService.auditThrowableAbstractProblem(patronRequest, "Pickup patron : not found", error)
			.doOnSuccess(__ -> log.warn("checkIfPatronExistsAtPickupAgency is false, creating new patron record prid={}",patronRequest.getId()))
			.flatMap(audit -> createPatronAtPickupAgency(requestWorkflowContext));
	}

	private Function<MultipleVirtualPatronsFound, Mono<PatronIdentity>> handleMultipleVirtualPatronsFound(RequestWorkflowContext requestWorkflowContext) {

		final var patronRequest = requestWorkflowContext.getPatronRequest();

		return error -> patronRequestAuditService.auditThrowableAbstractProblem(patronRequest, "Pickup patron : multiple found", error)
			.doOnSuccess(__ -> log.warn("Multiple virtual patrons found at pickup agency={}", requestWorkflowContext.getPickupSystemCode()))
			.flatMap(audit -> Mono.error(error));
	}

	private Mono<PatronIdentity> updateLocalPatronIdentityForLmsPatron(
		Patron patron, PatronRequest patronRequest, String hostLmsCode) {

		log.debug("updateLocalPatronIdentityForLmsPatron {}",patron);

		String barcodes_as_string = ((patron.getLocalBarcodes() != null)
			&& (patron.getLocalBarcodes().size() > 0))
			? patron.getLocalBarcodes().toString()
			: null;

		if (barcodes_as_string == null) {
			log.warn("Pickup Patron will have no barcodes {}/{}",patronRequest,patron);
		}

		return checkPatronType( patron.getLocalId().get(0), patron.getLocalPatronType(), patronRequest, hostLmsCode)
			.flatMap(function((localId, patronType) -> checkForPatronIdentity(patronRequest, hostLmsCode, localId, patronType, barcodes_as_string)));
	}

	private Mono<Tuple2<String, String>> checkPatronType(String localId,
																											 String patronType, PatronRequest patronRequest, String hostLmsCode) {

		log.debug("checkPatronType id of patron at home system={}, type of patron at home system={} pickupSystem={}", localId, patronType, hostLmsCode);

		// Work out what the global patronId is we are using for this real patron
		return getRequestingIdentity(patronRequest)
			.doOnNext(requestingIdentity -> log.debug("checkPatronType checking requesting identity {} at {}",requestingIdentity, hostLmsCode) )
			// Work out the ???
			.flatMap(requestingIdentity -> determinePatronType(hostLmsCode, requestingIdentity))
			.doOnNext(newlyMappedVPatronType -> log.debug("Testing to see if patron type needs to be updated from {} to {}",patronType,newlyMappedVPatronType) )
			.flatMap(newlyMappedVPatronType -> {

				if (patronType == null || newlyMappedVPatronType == null) {
					throw new NullPointerException("One or both values are null: " +
						"known patron type = " + patronType + ", " +
						"determined patron type = " + newlyMappedVPatronType);
				}

				// if the returned value and the stored value were different, update the virtual patron
				else if (!Objects.equals(newlyMappedVPatronType, patronType)) {
					return updateVirtualPatron(hostLmsCode, localId, newlyMappedVPatronType)

						// check the patron type was infact updated by checking what was returned
						.flatMap(returnedPatron -> {

							final var returnedLocalPatronType = returnedPatron.getLocalPatronType();

							if (!returnedLocalPatronType.equals(newlyMappedVPatronType)) { // then unsuccessful update

								var auditData = new HashMap<String, Object>();
								auditData.put("pickup-dcbPatronId", localId);
								auditData.put("pickup-hostLmsCode", hostLmsCode);
								auditData.put("pickup-dcbLocalPatronType-before-update", patronType);
								auditData.put("pickup-dcbLocalPatronType-after-update", returnedLocalPatronType);
								auditData.put("pickup-dcbLocalPatronType-desired-update", newlyMappedVPatronType);
								auditData.put("pickup-patron-returned-from-update", returnedPatron.toString());

								final var auditMessage = String.format("Patron update failed : localId %s ptype %s hostlms %s",
									localId, newlyMappedVPatronType, hostLmsCode);

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

	private Mono<Patron> updateVirtualPatron(String pickupHostLmsCode, String localId, String patronType) {
		log.debug("updateVirtualPatron {}, {}", localId, patronType);

		return hostLmsService.getClientFor(pickupHostLmsCode)
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

	private Mono<PatronIdentity> createPatronAtPickupAgency(RequestWorkflowContext requestWorkflowContext) {

		final var hostLmsCode = requestWorkflowContext.getPickupSystemCode();
		final var patronRequest = requestWorkflowContext.getPatronRequest();

		log.debug("createPatronAtPickupAgency prid={}, hostLmsCode={}",
			patronRequest.getId(),hostLmsCode);

		return hostLmsService.getClientFor(hostLmsCode)
			.zipWhen(client -> getRequestingIdentity(patronRequest), Tuples::of)
			.flatMap(function((client,requestingIdentity) ->
				createVPatronAndSaveIdentity(client, requestingIdentity, patronRequest, hostLmsCode)));
	}

	/**
	 * Create a virtual patron at the pickup library and then store the details of that record in a patron identity record, returning the patronIdentity
	 */
	private Mono<PatronIdentity> createVPatronAndSaveIdentity(
		HostLmsClient client,
		PatronIdentity requestingIdentity,
		PatronRequest patronRequest,
		String hostLmsCode) {

		return createPatronAtPickupAgency(patronRequest, client, requestingIdentity, hostLmsCode)
			.flatMap(function((localId, patronType) -> checkForPatronIdentity(patronRequest, hostLmsCode, localId, patronType, requestingIdentity.getLocalBarcode())))
			.flatMap( auditPickupPatron(patronRequest, "Pickup patron : created") );
	}

	private Mono<Tuple2<String, String>> createPatronAtPickupAgency(
		PatronRequest patronRequest, HostLmsClient client,
		PatronIdentity requestingPatronIdentity, String hostLmsCode) {
		// Using the patron type from the patrons "Home" patronIdentity, look up what the equivalent patron type is at
		// the pickup system. Then create a patron in the pickup system using that type value.

		log.debug("createPatronAtPickupAgency");

		// Patrons can have multiple barcodes. To keep the domain model sane(ish) we store [b1, b2, b3] (As the result of Objects.toString()
		// in the field. Here we unpack that structure back into an array of barcodes that the HostLMS can do with as it pleases
		final List<String> patron_barcodes = parseList( requestingPatronIdentity.getLocalBarcode() );

		if ((patron_barcodes == null) || (patron_barcodes.size() == 0)) {
			log.warn("Pickup patron has no barcodes. Source identity {}. Will be unable to check out to this patron",
				requestingPatronIdentity);
		}

		return determinePatronType(hostLmsCode, requestingPatronIdentity)
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
							.localItemId(patronRequest.getPickupItemId())
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

	private Mono<String> determinePatronType(String pickupHostLmsCode,
																					 PatronIdentity requestingIdentity) {

		log.debug("determinePatronType");

		if (pickupHostLmsCode == null || requestingIdentity == null
			|| requestingIdentity.getHostLms() == null || requestingIdentity.getHostLms().getCode() == null) {

			throw new RuntimeException("Missing patron data - unable to determine patron type at pickup agency:" + pickupHostLmsCode);
		}

		// log.debug("determinePatronType {} {} {} requesting identity present",pickupHostLmsCode, requestingIdentity.getHostLms().getCode(),
		//	requestingIdentity.getLocalPtype());

		// We need to look up the requestingHostLmsCode and not pass pickupHostLmsCode
		return patronTypeService.determinePatronType(pickupHostLmsCode,
			requestingIdentity.getHostLms().getCode(),
			requestingIdentity.getLocalPtype(), requestingIdentity.getLocalId());
	}

	public Mono<Patron> getPatron(RequestWorkflowContext ctx) {

		final var pickupIdentity = ctx.getPickupPatronIdentity();

		return hostLmsService.getClientFor(ctx.getPickupSystemCode())
			.flatMap(hostLmsClient -> hostLmsClient.getPatronByLocalId(pickupIdentity.getLocalId()));
	}

	private Function<PatronIdentity, Mono<PatronIdentity>> auditPickupPatron(PatronRequest patronRequest, String message) {
		return patronIdentity -> {

			final var auditData = new HashMap<String, Object>();

			var pickupPatron =  PickupPatron.builder()
				.dcbPatronIdentityID(patronIdentity.getId().toString())
				.localSystemId(patronIdentity.getLocalId());

			try { // adding unique id to the audit data
				var determineUniqueId = patronIdentity.getPatron().determineUniqueId();
				pickupPatron.determinedUniqueId(determineUniqueId);
			} catch (Exception e) {
				log.error("Failed to add unique id to pickup patron audit", e);
				pickupPatron.determinedUniqueId(e.toString());
			}

			auditData.put("PickupPatron", pickupPatron.build());

			return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
				.thenReturn(patronIdentity);
		};
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PickupPatron {
		@Nullable
		private String localSystemId;
		@Nullable
		private String dcbPatronIdentityID;
		@Nullable
		private String determinedUniqueId;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final PatronRequest patronRequest = ctx.getPatronRequest();
		final boolean isStatusApplicable = getPossibleSourceStatus().contains(patronRequest.getStatus());
		final boolean isPickupAnywhereWorkflow = "RET-PUA".equals(patronRequest.getActiveWorkflow());

		return isStatusApplicable && isPickupAnywhereWorkflow;
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.REQUEST_PLACED_AT_PICKUP_AGENCY);
	}

  @Override     
  public String getName() {
    return "PlacePatronRequestAtPickupAgencyStateTransition";
  }

	@Override
	public boolean attemptAutomatically() {
		return true;
	}
}

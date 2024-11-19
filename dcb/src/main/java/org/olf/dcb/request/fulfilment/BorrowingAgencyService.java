package org.olf.dcb.request.fulfilment;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.model.*;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.request.resolution.SharedIndexService;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronIdentityRepository;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple5;

import java.time.Duration;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;
import static services.k_int.utils.ReactorUtils.raiseError;

@Slf4j
@Prototype
public class BorrowingAgencyService {
	private final HostLmsService hostLmsService;
	private final PatronIdentityRepository patronIdentityRepository;
	private final SupplierRequestService supplierRequestService;
	private final SharedIndexService sharedIndexService;
	private final AgencyService agencyService;
	private final PatronRequestAuditService patronRequestAuditService;

	// Provider to prevent circular reference exception by allowing lazy access to
	// this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	public BorrowingAgencyService(HostLmsService hostLmsService,
		PatronIdentityRepository patronIdentityRepository,
		SupplierRequestService supplierRequestService, SharedIndexService sharedIndexService,
		AgencyService agencyService,
		PatronRequestAuditService patronRequestAuditService,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider) {

		this.hostLmsService = hostLmsService;
		this.patronIdentityRepository = patronIdentityRepository;
		this.supplierRequestService = supplierRequestService;
		this.sharedIndexService = sharedIndexService;
		this.agencyService = agencyService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
	}

	public Mono<PatronRequest> placePatronRequestAtBorrowingAgency(RequestWorkflowContext ctx) {

		PatronRequest patronRequest = ctx.getPatronRequest();
		log.info("placePatronRequestAtBorrowingAgency {}", patronRequest.getId());

		return fetchRequiredData(patronRequest, ctx)
			.flatMap(function(this::borrowingRequestFlow))
			.map(patronRequest::placedAtBorrowingAgency)
			;
	}

	public Mono<RequestWorkflowContext> cleanUp(RequestWorkflowContext requestWorkflowContext) {

		final var patronRequest = getValueOrNull(requestWorkflowContext, RequestWorkflowContext::getPatronRequest);
		final var hostLmsCode = getValueOrNull(patronRequest, PatronRequest::getPatronHostlmsCode);

		log.info("WORKFLOW cleanUp {}", patronRequest);

		if (hostLmsCode != null && patronRequest != null) {
			return Mono.from(hostLmsService.getClientFor(patronRequest.getPatronHostlmsCode()))
				.flatMap(client -> deleteItemIfPresent(client, patronRequest) )
				.flatMap(client -> deleteBibIfPresent(client, patronRequest) )
				.thenReturn(requestWorkflowContext);
		}

		final var message = "Borrower cleanup : Skipped";
		final var auditData = new HashMap<String, Object>();
		auditData.put("hostLmsCode", getValue(hostLmsCode, "No value present"));
		auditData.put("patronRequest", patronRequest != null ? "Exists" : "No value present");
		return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
			.flatMap(audit -> Mono.just(requestWorkflowContext));
	}

	public Mono<HostLmsClient> deleteItemIfPresent(HostLmsClient client, PatronRequest patronRequest) {

		final var localItemId = getValueOrNull(patronRequest, PatronRequest::getLocalItemId);
		final var localItemStatus = getValueOrNull(patronRequest, PatronRequest::getLocalItemStatus);

		if (localItemId != null && !"MISSING".equals(localItemStatus)) {

			return checkItemExists(client, localItemId, patronRequest)
				.flatMap(_client -> _client.deleteItem(localItemId))

				// Catch any skipped deletions
				.switchIfEmpty(Mono.defer(() -> Mono.just("OK")))

				// Genuine error we didn't account for
				.onErrorResume(logAndReturnErrorString("Delete virtual item : Failed", patronRequest))
				.thenReturn(client);
		}
		else {
			final var message = "Delete virtual item : Skipped";
			final var auditData = new HashMap<String, Object>();
			auditData.put("localItemId", patronRequest.getLocalItemId() != null ? patronRequest.getLocalItemId() : "No value present");
			auditData.put("localItemStatus", getValue(localItemStatus, "No value present"));
			return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
				.flatMap(audit -> Mono.just(client));
		}
	}

	private Function<Throwable, Mono<String>> logAndReturnErrorString(String message, PatronRequest patronRequest) {

		return error -> {
			final var auditData = new HashMap<String, Object>();
			auditThrowable(auditData, "Throwable", error);

			return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
				.flatMap(audit -> Mono.just("Error"));
		};
	}

	private Mono<HostLmsClient> checkItemExists(HostLmsClient client, String localItemId, PatronRequest patronRequest) {

		final var localRequestId = getValueOrNull(patronRequest, PatronRequest::getLocalRequestId);

		return client.getItem(localItemId, localRequestId)
			.flatMap(item -> {

				// if the item exists a local id will be present
				if (item != null && item.getLocalId() != null) {

					// return the client to proceed with deletion
					return Mono.just(client);
				}

				// no local id to delete, skip delete by passing back an empty
				final var message = "Delete virtual item : Skipped";
				final var auditData = new HashMap<String, Object>();
				auditData.put("item", item);
				return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData).flatMap(audit -> Mono.empty());
			})
			.onErrorResume(error -> {
				// we encountered an error when confirming the item exists
				final var message = "Delete virtual item : Skipped";
				final var auditData = new HashMap<String, Object>();
				auditThrowable(auditData, "Throwable", error);
				return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData).flatMap(audit -> Mono.empty());
			});
	}

	public Mono<HostLmsClient> deleteBibIfPresent(HostLmsClient client, PatronRequest patronRequest) {
		if (patronRequest.getLocalBibId() != null) {

			final var localBibId = patronRequest.getLocalBibId();

			return client.deleteBib(localBibId)
				// Genuine error we didn't account for
				.onErrorResume(logAndReturnErrorString("Delete virtual bib : Failed", patronRequest))
				.thenReturn(client);
		}
    else {
			final var message = "Delete virtual bib : Skipped";
			final var auditData = new HashMap<String, Object>();
			auditData.put("localBibId", patronRequest.getLocalBibId() != null ? patronRequest.getLocalBibId() : "No value present");
			return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
				.flatMap(audit -> Mono.just(client));
    }
  }

	private Mono<Tuple2<PatronRequest, String>> createVirtualBib(
		RequestWorkflowContext ctx, PatronRequest patronRequest, HostLmsClient hostLmsClient) {

		final UUID bibClusterId = patronRequest.getBibClusterId();

		log.info("createVirtualBib for cluster {}", bibClusterId);

		if (hostLmsClient == null) {
			log.error("Cannot create a bib item at host system because hostLmsClient is NULL");
			throw new DcbError("Cannot create a bib item at host system because hostLmsClient is NULL");
		}

		return sharedIndexService.findSelectedBib(bibClusterId)
			.map(this::extractBibData)
			.flatMap(bib -> Mono.zip(
				hostLmsClient.createBib(bib).map(patronRequest::setLocalBibId),
				Mono.just(bib.getTitle())
			))
			.switchIfEmpty(Mono.error(new DcbError(
				"Failed to create virtual bib at " + hostLmsClient.getHostLmsCode() + " for cluster " + bibClusterId)));
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

	private Mono<Tuple3<PatronRequest, String, String>> createVirtualItem(
		PatronRequest patronRequest, PatronIdentity borrowingIdentity, HostLmsClient hostLmsClient,
		SupplierRequest supplierRequest, String bibRecordTitle) {

		return getSupplyingAgencyCode(supplierRequest)
			.flatMap(supplyingAgencyCode -> Mono.zip(
				virtualItemRequest(patronRequest, borrowingIdentity, hostLmsClient, supplierRequest, supplyingAgencyCode),
				Mono.just(bibRecordTitle),
				Mono.just(supplyingAgencyCode)
			));
	}

	private Mono<PatronRequest> virtualItemRequest(PatronRequest patronRequest, PatronIdentity patronIdentity,
		HostLmsClient hostLmsClient, SupplierRequest supplierRequest, String supplyingAgencyCode) {

		log.info("virtualItemRequest(...)");

		final String localBibId = patronRequest.getLocalBibId();
		Objects.requireNonNull(localBibId, "Local bib ID not set on Patron Request");

		log.info("virtualItemRequest for localBibId {}/{}", localBibId, supplierRequest.getLocalItemLocationCode());
		log.info("slToAgency:{} {} {} {} {}", "Location", supplierRequest.getHostLmsCode(),
			supplierRequest.getLocalItemLocationCode(), "AGENCY", "DCB");

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
				localBibId,

				// supplier information
				supplyingAgencyCode,
				supplierRequest.getHostLmsCode(),
				supplierRequest.getLocalItemBarcode(),
				supplierRequest.getCanonicalItemType(),

				patronIdentity.getLocalHomeLibraryCode()))
			.map(patronRequest::addLocalItemDetails)
			.switchIfEmpty(Mono.defer(() -> Mono.error(new DcbError("Failed to create virtual item."))));
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

	private Mono<LocalRequest> borrowingRequestFlow(RequestWorkflowContext ctx,
		PatronRequest patronRequest, PatronIdentity borrowingIdentity,
		HostLmsClient hostLmsClient, SupplierRequest supplierRequest) {

		return createVirtualBib(ctx, patronRequest, hostLmsClient)
			// Have a suspicion that Polaris needs breathing space in between the virtual bib and the virtual item
			.flatMap(tuple -> {
				final var pr = tuple.getT1();
				final var bibRecordTitle = tuple.getT2();

				return createVirtualItem(pr, borrowingIdentity, hostLmsClient, supplierRequest, bibRecordTitle);
			})
			.flatMap(tuple -> {
				final var pr = tuple.getT1();
				final var bibRecordTitle = tuple.getT2();
				final var supplyingAgencyCode = tuple.getT3();

				return createHoldRequest(
					ctx, pr, borrowingIdentity, hostLmsClient, supplierRequest, bibRecordTitle, supplyingAgencyCode)
					.delaySubscription(Duration.ofSeconds(5))
					.doOnSuccess(localRequest -> log.debug("borrowingRequestFlow returned: {}", localRequest));
			})
			.switchIfEmpty( Mono.defer(() -> Mono.error(new DcbError("Failed to place hold request."))) );
	}

	private static Mono<LocalRequest> createHoldRequest(RequestWorkflowContext ctx,
		PatronRequest patronRequest, PatronIdentity borrowingIdentity,
		HostLmsClient hostLmsClient, SupplierRequest supplierRequest, String bibRecordTitle, String supplyingAgencyCode) {
		// var note = "Consortial Hold. tno=" + patronRequest.getId();
    String note = ctx.generateTransactionNote();


		return hostLmsClient.placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters.builder()
			.localPatronId(borrowingIdentity.getLocalId())
			.localPatronBarcode(borrowingIdentity.getLocalBarcode())
			.localBibId(patronRequest.getLocalBibId())
			.localItemId(patronRequest.getLocalItemId())
			.pickupLocationCode(patronRequest.getPickupLocationCode())
			.pickupLocation(ctx.getPickupLocation())
			.note(note)
			.patronRequestId(patronRequest.getId().toString())
			.title(bibRecordTitle)
			.supplyingAgencyCode(supplyingAgencyCode)
			.supplyingLocalItemId(supplierRequest.getLocalItemId())
			.supplyingLocalItemBarcode(supplierRequest.getLocalItemBarcode())
			.canonicalItemType(supplierRequest.getCanonicalItemType())
			.build());
	}

	private Mono<Tuple5<RequestWorkflowContext, PatronRequest, PatronIdentity, HostLmsClient, SupplierRequest>> fetchRequiredData(
		PatronRequest patronRequest, RequestWorkflowContext ctx) {

		final var patronId = patronRequest.getPatron().getId();

		return Mono.from(patronIdentityRepository.findOneByPatronIdAndHomeIdentity(patronId, Boolean.TRUE))
			.flatMap(borrowingIdentity -> Mono.zip(
				Mono.just(ctx),
				Mono.just(patronRequest),
				Mono.just(borrowingIdentity),
				fetchClientFor(borrowingIdentity),
				fetchSupplierRequestFor(patronRequest)
			));
	}

	private Mono<HostLmsClient> fetchClientFor(PatronIdentity borrowingIdentity) {
		return hostLmsService.getClientFor(borrowingIdentity.getHostLms().code)
			.switchIfEmpty(Mono.defer(() -> Mono.error(new DcbError("Failed to get HostLmsClient."))));
	}

	private Mono<SupplierRequest> fetchSupplierRequestFor(PatronRequest patronRequest) {
		return supplierRequestService.findSupplierRequestFor(patronRequest)
			.switchIfEmpty(Mono.defer(() -> Mono.error(new DcbError("Failed to find SupplierRequest."))));
	}

	public Mono<HostLmsItem> getItem(PatronRequest patronRequest) {
		if (patronRequest.getPatronHostlmsCode() != null) {
			return Mono.from(hostLmsService.getClientFor(patronRequest.getPatronHostlmsCode()))
				.flatMap(hostLmsClient -> hostLmsClient.getItem(patronRequest.getLocalItemId(), patronRequest.getLocalRequestId()));
		}

		return raiseError(new DcbError("Can not get virtual records status with null HostLmsCode"));
	}
}

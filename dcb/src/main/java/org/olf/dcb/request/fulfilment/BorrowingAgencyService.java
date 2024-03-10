package org.olf.dcb.request.fulfilment;

import static reactor.function.TupleUtils.function;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.request.resolution.SharedIndexService;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.storage.PatronIdentityRepository;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.function.*;

@Slf4j
@Prototype
public class BorrowingAgencyService {
	private final HostLmsService hostLmsService;
	private final PatronIdentityRepository patronIdentityRepository;
	private final SupplierRequestService supplierRequestService;
	private final SharedIndexService sharedIndexService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;

	// Provider to prevent circular reference exception by allowing lazy access to
	// this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	public BorrowingAgencyService(HostLmsService hostLmsService,
		PatronIdentityRepository patronIdentityRepository,
		SupplierRequestService supplierRequestService, SharedIndexService sharedIndexService,
		LocationToAgencyMappingService locationToAgencyMappingService,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider) {

		this.hostLmsService = hostLmsService;
		this.patronIdentityRepository = patronIdentityRepository;
		this.supplierRequestService = supplierRequestService;
		this.sharedIndexService = sharedIndexService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
	}

	public Mono<PatronRequest> placePatronRequestAtBorrowingAgency(RequestWorkflowContext ctx) {

		PatronRequest patronRequest = ctx.getPatronRequest();
		log.info("placePatronRequestAtBorrowingAgency {}", patronRequest.getId());

		return fetchRequiredData(patronRequest, ctx)
			.flatMap(function(this::borrowingRequestFlow))
			.map(function(patronRequest::placedAtBorrowingAgency))
			.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest))
			;
	}

	public Mono<String> cleanUp(PatronRequest patronRequest) {
		log.info("WORKFLOW cleanUp {}", patronRequest);
		if (patronRequest.getPatronHostlmsCode() != null) {
			return Mono.from(hostLmsService.getClientFor(patronRequest.getPatronHostlmsCode()))
				.flatMap(client -> deleteItemIfPresent(client, patronRequest) )
				.flatMap(client -> deleteBibIfPresent(client, patronRequest) )
				.thenReturn("OK")
				.defaultIfEmpty("ERROR");
		}

		return Mono.just("ERROR");
	}

	public Mono<HostLmsClient> deleteItemIfPresent(HostLmsClient client, PatronRequest patronRequest) {
		if (patronRequest.getLocalItemId() != null) {
			return client.deleteItem(patronRequest.getLocalItemId())
				.thenReturn(client);
		}
		else {
			return Mono.just(client);
		}
	}

  public Mono<HostLmsClient> deleteBibIfPresent(HostLmsClient client, PatronRequest patronRequest) {
		if (patronRequest.getLocalBibId() != null) {
			return client.deleteBib(patronRequest.getLocalBibId())
        .thenReturn(client);
    }
    else {
      return Mono.just(client);
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

		return getAgencyForShelvingLocation(supplierRequest.getHostLmsCode(), supplierRequest.getLocalItemLocationCode())
			.flatMap(referenceValueMapping -> Mono.zip(
				virtualItemRequest(patronRequest, borrowingIdentity, hostLmsClient, supplierRequest, referenceValueMapping),
				Mono.just(bibRecordTitle),
				Mono.just(referenceValueMapping.getToValue())
			));
	}

	private Mono<PatronRequest> virtualItemRequest(PatronRequest patronRequest, PatronIdentity patronIdentity,
		HostLmsClient hostLmsClient, SupplierRequest supplierRequest, ReferenceValueMapping referenceValueMapping) {

		log.info("virtualItemRequest(...)");

		final String localBibId = patronRequest.getLocalBibId();
		Objects.requireNonNull(localBibId, "Local bib ID not set on Patron Request");

		log.info("virtualItemRequest for localBibId {}/{}", localBibId, supplierRequest.getLocalItemLocationCode());
		log.info("slToAgency:{} {} {} {} {}", "Location", supplierRequest.getHostLmsCode(),
			supplierRequest.getLocalItemLocationCode(), "AGENCY", "DCB");

		String agencyCode = referenceValueMapping.getToValue();
		supplierRequest.setLocalAgency(agencyCode);

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
				agencyCode,
				supplierRequest.getLocalItemBarcode(),
				supplierRequest.getCanonicalItemType(),
				patronIdentity.getLocalHomeLibraryCode()))
			.map(hostLmsItem -> patronRequest.setLocalItemId(hostLmsItem.getLocalId()))
			.switchIfEmpty(Mono.defer(() -> Mono.error(new DcbError("Failed to create virtual item."))));
	}

	private Mono<ReferenceValueMapping> getAgencyForShelvingLocation(String context, String code) {
		return locationToAgencyMappingService.findLocationToAgencyMapping(context, code)
			.doOnSuccess(rvm -> log.debug("getAgencyForShelvingLocation looked up "+rvm.getToValue()+" for " + context+":"+code))
			.switchIfEmpty(Mono.defer(() -> Mono.error(
				new DcbError("Failed to resolve shelving loc "+context+":"+code+" to agency"))));
	}

	private Mono<Tuple2<String, String>> borrowingRequestFlow(RequestWorkflowContext ctx,
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
					ctx, pr, borrowingIdentity, hostLmsClient, supplierRequest, bibRecordTitle, supplyingAgencyCode);
			})
			.transform(extractLocalIdAndLocalStatus())
			.switchIfEmpty( Mono.defer(() -> Mono.error(new DcbError("Failed to place hold request."))) );
	}

	private static Function<Mono<LocalRequest>, Publisher<Tuple2<String, String>>> extractLocalIdAndLocalStatus() {
		return mono -> mono.map(localRequest -> Tuples.of(localRequest.getLocalId(), localRequest.getLocalStatus()));
	}

	private static Mono<LocalRequest> createHoldRequest(RequestWorkflowContext ctx,
		PatronRequest patronRequest, PatronIdentity borrowingIdentity,
		HostLmsClient hostLmsClient, SupplierRequest supplierRequest, String bibRecordTitle, String supplyingAgencyCode) {
		var note = "Consortial Hold. tno=" + patronRequest.getId();

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
}

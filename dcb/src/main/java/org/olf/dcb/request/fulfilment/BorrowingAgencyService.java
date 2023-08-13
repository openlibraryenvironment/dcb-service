package org.olf.dcb.request.fulfilment;

import static reactor.function.TupleUtils.function;

import java.util.Objects;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.olf.dcb.storage.ShelvingLocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

@Prototype
public class BorrowingAgencyService {
	private static final Logger log = LoggerFactory.getLogger(BorrowingAgencyService.class);

	private final HostLmsService hostLmsService;
	private final PatronIdentityRepository patronIdentityRepository;
	private final SupplierRequestService supplierRequestService;
	private final BibRepository bibRepository;
	private final ClusterRecordRepository clusterRecordRepository;

	// Provider to prevent circular reference exception by allowing lazy access to
	// this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;
	private final ReferenceValueMappingRepository referenceValueMappingRepository;

	public BorrowingAgencyService(HostLmsService hostLmsService, PatronIdentityRepository patronIdentityRepository,
			SupplierRequestService supplierRequestService, BibRepository bibRepository,
			ClusterRecordRepository clusterRecordRepository, ShelvingLocationRepository shelvingLocationRepository,
			PatronRequestRepository patronRequestRepository, ReferenceValueMappingRepository referenceValueMappingRepository,
			BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider) {

		this.hostLmsService = hostLmsService;
		this.patronIdentityRepository = patronIdentityRepository;
		this.supplierRequestService = supplierRequestService;
		this.bibRepository = bibRepository;
		this.clusterRecordRepository = clusterRecordRepository;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

	public Mono<PatronRequest> placePatronRequestAtBorrowingAgency(PatronRequest patronRequest) {
		log.debug("placePatronRequestAtBorrowingAgency {}", patronRequest.getId());

		return getHoldRequestData(patronRequest)
				.flatMap(function(this::createVirtualBib))
				.flatMap(function(this::createVirtualItem))
				.flatMap(function(this::placeHoldRequest))
				.map(function(patronRequest::placedAtBorrowingAgency))
				.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest));
	}

	private Mono<Tuple4<PatronRequest, PatronIdentity, HostLmsClient, SupplierRequest>> createVirtualBib(
			PatronRequest patronRequest, PatronIdentity patronIdentity, HostLmsClient hostLmsClient,
			SupplierRequest supplierRequest) {
		final UUID bibClusterId = patronRequest.getBibClusterId();
		log.debug("createVirtualBib for cluster {}", bibClusterId);
		return Mono.from(clusterRecordRepository.findById(bibClusterId))
				.flatMap(clusterRecord -> Mono.from(bibRepository.findById(clusterRecord.getSelectedBib())))
				.map(this::extractBibData).flatMap(hostLmsClient::createBib).map(patronRequest::setLocalBibId)
				.switchIfEmpty(Mono.error(new RuntimeException("Failed to create virtual bib.")))
				.map(pr -> Tuples.of(pr, patronIdentity, hostLmsClient, supplierRequest));
	}

	private Bib extractBibData(BibRecord bibRecord) {
		log.debug("extractBibData(bibRecord: {})", bibRecord);

		// Guard clause
		if (bibRecord.getTitle() == null) {
			throw new IllegalArgumentException("Missing title information.");
		}

		return Bib.builder().title(bibRecord.getTitle())
				.author(bibRecord.getAuthor() != null ? bibRecord.getAuthor().getName() : null).build();
	}

	private Mono<Tuple4<PatronRequest, PatronIdentity, HostLmsClient, String>> createVirtualItem(
			PatronRequest patronRequest, 
			PatronIdentity patronIdentity, 
			HostLmsClient hostLmsClient,
			SupplierRequest supplierRequest) {

		final String localBibId = patronRequest.getLocalBibId();
		Objects.requireNonNull(localBibId, "Local bib ID not set on Patron Request");

		log.debug("createVirtualItem for localBibId {}/{}", localBibId, supplierRequest.getLocalItemLocationCode());
		log.debug("slToAgency:{} {} {} {} {}", "ShelvingLocation", supplierRequest.getHostLmsCode(),
				supplierRequest.getLocalItemLocationCode(), "AGENCY", "DCB");

		return Mono
				.from(referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
						"ShelvingLocation", supplierRequest.getHostLmsCode(), supplierRequest.getLocalItemLocationCode(), "AGENCY",
						"DCB"))
				.doOnSuccess(mapping -> log.debug("Result from getting agency for shelving location: {}", mapping))
				.flatMap(mapping -> {
					String agencyCode = mapping.getToValue();
					supplierRequest.setLocalAgency(agencyCode);
					return hostLmsClient.createItem(localBibId, agencyCode, supplierRequest.getLocalItemBarcode());
				})
				.map(HostLmsItem::getLocalId)
				// .doOnNext(patronRequest::setLocalItemId) - replace with map
				.map(localItemId -> { 
					patronRequest.setLocalItemId(localItemId);
					return Tuples.of(patronRequest, patronIdentity, hostLmsClient, localItemId);
				})
				.switchIfEmpty(Mono.error(new RuntimeException("Failed to create virtual item.")));
	}

	private Mono<Tuple2<String, String>> placeHoldRequest(PatronRequest patronRequest, PatronIdentity patronIdentity,
			HostLmsClient hostLmsClient, String localItemId) {

		log.debug("placeHoldRequest for localItemId {} {}", localItemId, patronIdentity);

		String note = "Consortial Hold. tno=" + patronRequest.getId();
		return hostLmsClient
				.placeHoldRequest(patronIdentity.getLocalId(), "i", localItemId, patronRequest.getPickupLocationCode(), note,
						patronRequest.getId().toString())
				.map(response -> Tuples.of(response.getT1(), response.getT2()))
				.switchIfEmpty(Mono.error(new RuntimeException("Failed to place hold request.")));
	}

	private Mono<Tuple4<PatronRequest, PatronIdentity, HostLmsClient, SupplierRequest>> getHoldRequestData(
			PatronRequest patronRequest) {
		return Mono
				.from(
						patronIdentityRepository.findOneByPatronIdAndHomeIdentity(patronRequest.getPatron().getId(), Boolean.TRUE))
				.flatMap(pi -> hostLmsService.getClientFor(pi.getHostLms().code).map(client -> Tuples.of(pi, client))
						.switchIfEmpty(Mono.error(new RuntimeException("Failed to get HostLmsClient."))))
				.flatMap(tuple -> supplierRequestService.findSupplierRequestFor(patronRequest)
						.map(supplierRequest -> Tuples.of(patronRequest, tuple.getT1(), tuple.getT2(), supplierRequest))
						.switchIfEmpty(Mono.error(new RuntimeException("Failed to find SupplierRequest."))));
	}
}

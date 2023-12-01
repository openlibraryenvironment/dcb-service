package org.olf.dcb.request.fulfilment;

import static reactor.function.TupleUtils.function;

import java.util.Objects;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple5;
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

	public BorrowingAgencyService(HostLmsService hostLmsService,
		PatronIdentityRepository patronIdentityRepository,
		SupplierRequestService supplierRequestService, BibRepository bibRepository,
		ClusterRecordRepository clusterRecordRepository,
		ReferenceValueMappingRepository referenceValueMappingRepository,
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

	public Mono<String> cleanUp(PatronRequest patronRequest) {
		log.debug("cleanUp {}", patronRequest);
		if (patronRequest.getPatronHostlmsCode() != null) {
			return Mono.from(hostLmsService.getClientFor(patronRequest.getPatronHostlmsCode())).flatMap(client -> {
				if (patronRequest.getLocalItemId() != null)
					client.deleteItem(patronRequest.getLocalItemId());
				else
					log.info("No local item to delete at borrower system");
				if (patronRequest.getLocalBibId() != null)
					client.deleteBib(patronRequest.getLocalBibId());
				else
					log.info("No local bib to delete at borrower system");
				return Mono.just(patronRequest);
			}).thenReturn("OK").defaultIfEmpty("ERROR");
		}

		return Mono.just("ERROR");
	}

	private Mono<Tuple4<PatronRequest, PatronIdentity, HostLmsClient, SupplierRequest>> createVirtualBib(
			PatronRequest patronRequest, PatronIdentity patronIdentity, HostLmsClient hostLmsClient,
			SupplierRequest supplierRequest) {

		final UUID bibClusterId = patronRequest.getBibClusterId();

		if (hostLmsClient == null) {
			log.error("Cannot create a bib item at host system because hostLmsClient is NULL");
			throw new RuntimeException("Cannot create a bib item at host system because hostLmsClient is NULL");
		}

		log.debug("createVirtualBib for cluster {}", bibClusterId);

		return findSelectedBib(bibClusterId)
			.map(this::extractBibData)
			.flatMap(hostLmsClient::createBib)
			.map(patronRequest::setLocalBibId)
			.switchIfEmpty(Mono.error(new RuntimeException(
				"Failed to create virtual bib at " + hostLmsClient.getHostLmsCode() + " for cluster " + bibClusterId)))
			.map(pr -> Tuples.of(pr, patronIdentity, hostLmsClient, supplierRequest));
	}

	private Mono<BibRecord> findSelectedBib(UUID bibClusterId) {
		return getClusterRecord(bibClusterId)
			.flatMap(this::getSelectedBib);
	}

	private Mono<ClusterRecord> getClusterRecord(UUID clusterId) {
		return Mono.from(clusterRecordRepository.findById(clusterId))
			.switchIfEmpty(Mono.error(new RuntimeException("Unable to locate cluster record "+clusterId)));
	}

	private Mono<BibRecord> getSelectedBib(ClusterRecord cr) {
		return Mono.from(bibRepository.findById(cr.getSelectedBib()))
			.switchIfEmpty(Mono.error(new RuntimeException("Unable to locate selected bib "+cr.getSelectedBib()+" for cluster "+cr.getId())));
	}

	private Bib extractBibData(BibRecord bibRecord) {
		log.debug("extractBibData(bibRecord: {})", bibRecord);

		// Guard clause
		if (bibRecord.getTitle() == null) {
			throw new IllegalArgumentException("Missing title information.");
		}

		return Bib.builder().title(bibRecord.getTitle())
			.author(bibRecord.getAuthor() != null ? bibRecord.getAuthor().getName() : null)
			.build();
	}

	private Mono<Tuple5<PatronRequest, PatronIdentity, HostLmsClient, String, String>> createVirtualItem(
			PatronRequest patronRequest, PatronIdentity patronIdentity, HostLmsClient hostLmsClient,
			SupplierRequest supplierRequest) {

		log.debug("createVirtualItem(...)");

		final String localBibId = patronRequest.getLocalBibId();
		Objects.requireNonNull(localBibId, "Local bib ID not set on Patron Request");

		log.debug("createVirtualItem for localBibId {}/{}", localBibId, supplierRequest.getLocalItemLocationCode());
		log.debug("slToAgency:{} {} {} {} {}", "Location", supplierRequest.getHostLmsCode(),
				supplierRequest.getLocalItemLocationCode(), "AGENCY", "DCB");

		return getAgencyForShelvingLocation(supplierRequest.getHostLmsCode(), supplierRequest.getLocalItemLocationCode())
				.flatMap(mapping -> {
					String agencyCode = mapping.getToValue();
					supplierRequest.setLocalAgency(agencyCode);
					return hostLmsClient.createItem(new CreateItemCommand(localBibId, agencyCode,
							supplierRequest.getLocalItemBarcode(), supplierRequest.getCanonicalItemType()));
				}).map(HostLmsItem::getLocalId)
				.map(localItemId -> {
					patronRequest.setLocalItemId(localItemId);
					return Tuples.of(patronRequest, patronIdentity, hostLmsClient, localItemId, localBibId);
				}).switchIfEmpty(Mono.error(new RuntimeException("Failed to create virtual item.")));
	}

	private Mono<ReferenceValueMapping> getAgencyForShelvingLocation(String context, String code) {
		return Mono.from(referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
				"Location", context, code, "AGENCY", "DCB"))
			.doOnSuccess(rvm -> log.debug("looked up "+rvm.getToValue()+" for "+context+":"+code))
			.switchIfEmpty(Mono.error(new RuntimeException("Failed to resolve shelving loc "+context+":"+code+" to agency")));
	}

	private Mono<Tuple2<String, String>> placeHoldRequest(PatronRequest patronRequest,
		PatronIdentity patronIdentity, HostLmsClient hostLmsClient,
		String localItemId, String localBibId) {

		String note = "Consortial Hold. tno=" + patronRequest.getId();

		return hostLmsClient.placeHoldRequest(PlaceHoldRequestParameters.builder()
				.localPatronId(patronIdentity.getLocalId())
				.localBibId(localBibId)
				.localItemId(localItemId)
				.pickupLocation(patronRequest.getPickupLocationCode())
				.note(note)
				.patronRequestId(patronRequest.getId().toString())
				.build())
			.map(response -> Tuples.of(response.getLocalId(), response.getLocalStatus()))
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

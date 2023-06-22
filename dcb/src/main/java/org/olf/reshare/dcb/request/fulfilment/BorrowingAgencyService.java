package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.HostLmsItem;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;
import org.olf.reshare.dcb.storage.BibRepository;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;
import org.olf.reshare.dcb.storage.PatronIdentityRepository;
import org.olf.reshare.dcb.storage.ShelvingLocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuples;

import java.util.Map;
import java.util.UUID;

import static reactor.function.TupleUtils.function;

@Prototype
public class BorrowingAgencyService {
	private static final Logger log = LoggerFactory.getLogger(BorrowingAgencyService.class);

	private final HostLmsService hostLmsService;
	private final PatronIdentityRepository patronIdentityRepository;
	private final SupplierRequestService supplierRequestService;
	private final BibRepository bibRepository;
	private final ClusterRecordRepository clusterRecordRepository;
	private final ShelvingLocationRepository shelvingLocationRepository;
	private final PatronRequestTransitionErrorService errorService;

	public BorrowingAgencyService(HostLmsService hostLmsService,
		PatronIdentityRepository patronIdentityRepository,
		SupplierRequestService supplierRequestService,
		BibRepository bibRepository,
		ClusterRecordRepository clusterRecordRepository,
		ShelvingLocationRepository shelvingLocationRepository,
		PatronRequestTransitionErrorService errorService) {

		this.hostLmsService = hostLmsService;
		this.patronIdentityRepository = patronIdentityRepository;
		this.supplierRequestService = supplierRequestService;
		this.bibRepository = bibRepository;
		this.clusterRecordRepository = clusterRecordRepository;
		this.shelvingLocationRepository = shelvingLocationRepository;
		this.errorService = errorService;
	}

	public Mono<PatronRequest> placePatronRequestAtBorrowingAgency(PatronRequest patronRequest) {
		log.debug("placePatronRequestAtBorrowingAgency {}", patronRequest.getId());

		return getHoldRequestData(patronRequest)
			.flatMap(function(this::createVirtualBib))
			.flatMap(function(this::createVirtualItem))
			.flatMap(function(this::placeHoldRequest))
			.map(function(patronRequest::placedAtBorrowingAgency))
			.onErrorResume(error -> errorService.moveRequestToErrorStatus(error, patronRequest));
	}

	private Mono<Map<String,Object>> getCanonicalMetadata(UUID bibId) {
		return Mono.from(bibRepository.findById(bibId))
			.flatMap( bib -> Mono.just(bib.getCanonicalMetadata()) );
	}

	private Mono<Tuple5<PatronRequest, PatronIdentity, HostLmsClient, SupplierRequest, String>> createVirtualBib(
		PatronRequest patronRequest, PatronIdentity patronIdentity, HostLmsClient hostLmsClient,
		SupplierRequest supplierRequest) {
		final UUID bibClusterId = patronRequest.getBibClusterId();
		log.debug("createVirtualBib for cluster {}",bibClusterId);
		return Mono.from(clusterRecordRepository.findById(bibClusterId))
			.flatMap( clusterRecord -> Mono.from(bibRepository.findById(clusterRecord.getSelectedBib())))
			.flatMap( bibRecord -> Mono.just(bibRecord.getCanonicalMetadata()) )
			.flatMap( metadata -> {
				String title = extractMetadata(metadata,"title");
				Map<String,Object> authorMetadata = (Map<String,Object>) metadata.get("author");
				String author = authorMetadata != null ? extractMetadata(authorMetadata,"name") : null;
				return hostLmsClient.createBib(author,title);
			})
			.doOnNext(patronRequest::setLocalBibId)
			.switchIfEmpty(Mono.error(new RuntimeException("Failed to create virtual bib.")))
			.map(localBibId -> Tuples.of(patronRequest, patronIdentity, hostLmsClient, supplierRequest, localBibId));
	}

	private String extractMetadata(Map<String,Object>m, String accessPath) {
		String result = null;
		Object o = m.get(accessPath);
		if ( o != null )
			result = o.toString();
		return result;
	}

	private Mono<Tuple4<PatronRequest, PatronIdentity, HostLmsClient, String>> createVirtualItem(
		PatronRequest patronRequest, PatronIdentity patronIdentity, HostLmsClient hostLmsClient,
		SupplierRequest supplierRequest, String localBibId) {

		log.debug("createVirtualItem for localBibId {}", localBibId);

		return Mono.from(shelvingLocationRepository.findOneByCode(supplierRequest.getLocalItemLocationCode()))
			.doOnSuccess(shelvingLocation -> log.debug("Result from getting shelving location: {}", shelvingLocation))
			.flatMap(shelvingLocation -> {
				String agencyCode = shelvingLocation.getAgency() != null ? shelvingLocation.getAgency().getCode() : null;
				return hostLmsClient.createItem(localBibId, agencyCode, supplierRequest.getLocalItemBarcode());
			})
			.map(HostLmsItem::getLocalId)
			.doOnNext(patronRequest::setLocalItemId)
			.map(localItemId -> Tuples.of(patronRequest, patronIdentity, hostLmsClient, localItemId))
			.switchIfEmpty(Mono.error(new RuntimeException("Failed to create virtual item.")));
	}

	private Mono<Tuple2<String,String>> placeHoldRequest(PatronRequest patronRequest,
		PatronIdentity patronIdentity, HostLmsClient hostLmsClient, String localItemId) {

		log.debug("placeHoldRequest for localItemId {} {}",localItemId,patronIdentity);

		return hostLmsClient.placeHoldRequest(patronIdentity.getLocalId(), "i",
				localItemId, patronRequest.getPickupLocationCode())
			.map(response -> Tuples.of(response.getT1(), response.getT2()))
			.switchIfEmpty(Mono.error(new RuntimeException("Failed to place hold request.")));
	}

	private Mono<Tuple4<PatronRequest, PatronIdentity, HostLmsClient, SupplierRequest>> getHoldRequestData(
		PatronRequest patronRequest) {
		return Mono.from(patronIdentityRepository
				.findOneByPatronIdAndHomeIdentity(patronRequest.getPatron().getId(), Boolean.TRUE))
			.flatMap(pi -> hostLmsService.getClientFor(pi.getHostLms().code)
				.map(client -> Tuples.of(pi, client))
				.switchIfEmpty(Mono.error(new RuntimeException("Failed to get HostLmsClient."))))
			.flatMap(tuple -> supplierRequestService.findSupplierRequestFor(patronRequest)
				.map(supplierRequest -> Tuples.of(patronRequest, tuple.getT1(), tuple.getT2(), supplierRequest))
				.switchIfEmpty(Mono.error(new RuntimeException("Failed to find SupplierRequest."))));
	}
}

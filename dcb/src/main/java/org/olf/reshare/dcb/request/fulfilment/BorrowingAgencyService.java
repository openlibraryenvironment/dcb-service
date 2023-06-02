package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.HostLmsItem;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.SharedIndexService;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;
import org.olf.reshare.dcb.storage.PatronIdentityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuples;

import java.util.LinkedHashMap;
import java.util.UUID;

import static reactor.function.TupleUtils.function;

@Prototype
public class BorrowingAgencyService {
	private static final Logger log = LoggerFactory.getLogger(BorrowingAgencyService.class);

	private final SharedIndexService sharedIndexService;
	private final HostLmsService hostLmsService;
	private final PatronIdentityRepository patronIdentityRepository;
	private final SupplierRequestService supplierRequestService;

	public BorrowingAgencyService(SharedIndexService sharedIndexService,
		HostLmsService hostLmsService,
		PatronIdentityRepository patronIdentityRepository,
		SupplierRequestService supplierRequestService) {
		this.sharedIndexService = sharedIndexService;
		this.hostLmsService = hostLmsService;
		this.patronIdentityRepository = patronIdentityRepository;
		this.supplierRequestService = supplierRequestService;
	}

	public Mono<PatronRequest> placePatronRequestAtBorrowingAgency(PatronRequest patronRequest) {
		log.debug("placePatronRequestAtBorrowingAgency {}", patronRequest.getId());

		return getHoldRequestData(patronRequest)
			.flatMap(function(this::createVirtualBib))
			.flatMap(function(this::createVirtualItem))
			.flatMap(function(this::placeHoldRequest))
			.map(function(patronRequest::placedAtBorrowingAgency));
	}

	private Mono<Tuple5<PatronRequest, PatronIdentity, HostLmsClient, SupplierRequest, String>> createVirtualBib(
		PatronRequest patronRequest, PatronIdentity patronIdentity, HostLmsClient hostLmsClient,
		SupplierRequest supplierRequest) {
		final UUID bibClusterId = patronRequest.getBibClusterId();
		return Mono.zip(
				sharedIndexService.getCanonicalMetadataByBibClusterId(bibClusterId, "title")
					.cast(String.class)
					.switchIfEmpty(Mono.error(new RuntimeException("Failed to retrieve canonical title metadata."))),
				sharedIndexService.getCanonicalMetadataByBibClusterId(bibClusterId, "author")
					.cast(LinkedHashMap.class)
					.map(author -> author.get("name"))
					.cast(String.class)
					.switchIfEmpty(Mono.error(new RuntimeException("Failed to retrieve canonical author metadata."))))
			.flatMap(canonicalMetadata -> hostLmsClient.createBib(canonicalMetadata.getT2(), canonicalMetadata.getT1())
				.switchIfEmpty(Mono.error(new RuntimeException("Failed to create virtual bib."))))
			.map(localBibId -> Tuples.of(patronRequest, patronIdentity, hostLmsClient, supplierRequest, localBibId));
	}

	private Mono<Tuple4<PatronRequest, PatronIdentity, HostLmsClient, String>> createVirtualItem(
		PatronRequest patronRequest, PatronIdentity patronIdentity, HostLmsClient hostLmsClient,
		SupplierRequest supplierRequest, String localBibId) {
		return hostLmsClient.createItem(localBibId, supplierRequest.getLocalItemLocationCode(),
				supplierRequest.getLocalItemBarcode())
			.map(HostLmsItem::getLocalId)
			.map(localItemId -> Tuples.of(patronRequest, patronIdentity, hostLmsClient, localItemId))
			.switchIfEmpty(Mono.error(new RuntimeException("Failed to create virtual item.")));
	}

	private Mono<Tuple2<String,String>> placeHoldRequest(PatronRequest patronRequest,
		PatronIdentity patronIdentity, HostLmsClient hostLmsClient, String localItemId) {
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

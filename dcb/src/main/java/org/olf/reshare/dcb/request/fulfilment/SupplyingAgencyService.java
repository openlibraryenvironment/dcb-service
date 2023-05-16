package org.olf.reshare.dcb.request.fulfilment;

import java.util.Optional;
import java.util.stream.Collectors;

import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	public SupplyingAgencyService(
		HostLmsService hostLmsService, SupplierRequestService supplierRequestService,
		PatronService patronService, PatronTypeService patronTypeService) {

		this.hostLmsService = hostLmsService;
		this.supplierRequestService = supplierRequestService;
		this.patronService = patronService;
		this.patronTypeService = patronTypeService;
	}

	public Mono<PatronRequest> placePatronRequestAtSupplyingAgency(
		PatronRequest patronRequest) {

		log.debug("placePatronRequestAtSupplyingAgency {}", patronRequest.getId());

		return checkAndCreatePatronAtSupplier(patronRequest).map(PatronRequest::placedAtSupplyingAgency);
	}

	public Mono<PatronRequest> checkAndCreatePatronAtSupplier(
		PatronRequest patronRequest) {

		return findSupplierRequest(patronRequest)
			.flatMap(tuple -> checkIfPatronExistsAtSupplier(tuple.getT1(), tuple.getT2())
				.switchIfEmpty(Mono.defer(() -> createPatronAtSupplier(tuple.getT1(), tuple.getT2()))))
			.thenReturn(patronRequest);
	}

	public Mono<Tuple2<PatronRequest, SupplierRequest>> findSupplierRequest(PatronRequest pr) {
		return supplierRequestService
			.findAllSupplierRequestsFor(pr)
			.switchIfEmpty(Mono.error(new RuntimeException("No SupplierRequests found for PatronRequest")))
			// We assume that the first supplier request is all that is needed here
			.flatMap(sr -> Mono.zip(Mono.just(pr), Mono.just(sr.get(0))))
			.map(tuple -> Tuples.of(tuple.getT1(), tuple.getT2()));
	}

	private Mono<PatronIdentity> checkIfPatronExistsAtSupplier(PatronRequest pr, SupplierRequest sr) {
		log.debug("checkSupplierFor {}, {}", pr.getId(), sr.getId());

		return patronExistsAtSupplier(pr, sr)
			.flatMap(localId -> checkForPatronIdentity(pr, sr, localId));
	}

	private Mono<String> patronExistsAtSupplier(PatronRequest pr, SupplierRequest sr) {
		log.debug("patronExistsAtSupplier {}, {}", pr.getId(), sr.getId());

		final String uniqueId = getUniqueIdString(pr);
		log.debug("uniqueId {}", uniqueId);

		return hostLmsService.getClientFor(sr.getHostLmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.patronFind(uniqueId));
	}

	private Mono<PatronIdentity> checkForPatronIdentity(PatronRequest patronRequest,
		SupplierRequest supplierRequest, String localId) {

		log.debug("patronIdentityExists {}, {}, {}", patronRequest.getId(),
			supplierRequest.getId(), localId);

		return Mono.justOrEmpty(findIdentityByLocalId(patronRequest, localId))
			.switchIfEmpty(Mono.defer(() -> createIdentity(patronRequest,
				supplierRequest, localId)));
	}

	private static Optional<PatronIdentity> findIdentityByLocalId(
		PatronRequest patronRequest, String localId) {

		return patronRequest.getPatron().getPatronIdentities()
			.stream()
			.filter(pi -> pi.getLocalId().equals(localId))
			.findFirst();
	}

	private Mono<PatronIdentity> createPatronAtSupplier(PatronRequest patronRequest,
		SupplierRequest supplierRequest) {

		log.debug("createPatronForSupplier {}, {}", patronRequest.getId(), supplierRequest.getId());

		final String uniqueId = getUniqueIdString(patronRequest);

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.postPatron(uniqueId,
				patronTypeService.determinePatronType()))
			.switchIfEmpty(Mono.error(new RuntimeException("Unable to create a patron with uniqueId: " + uniqueId)))
			.flatMap(localId -> checkForPatronIdentity(patronRequest, supplierRequest, localId));
	}

	private String getUniqueIdString(PatronRequest patronRequest) {
		return patronRequest.getPatron().getPatronIdentities()
			.stream()
			.filter(PatronIdentity::getHomeIdentity)
			// localId@homeLibraryCode
			.map(pi -> pi.getLocalId() + "@" + patronRequest.getPatron().getHomeLibraryCode())
			.collect(Collectors.joining());
	}

	private Mono<PatronIdentity> createIdentity(PatronRequest patronRequest,
		SupplierRequest supplierRequest, String localId) {

		log.debug("createRequestor {}, {}, {}", patronRequest.getId(),
			supplierRequest.getId(), localId);

		return patronService.createPatronIdentity(patronRequest.getPatron(), localId,
			supplierRequest.getHostLmsCode(), false);
	}
}

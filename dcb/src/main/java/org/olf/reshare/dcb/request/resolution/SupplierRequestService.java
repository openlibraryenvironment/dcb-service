package org.olf.reshare.dcb.request.resolution;

import jakarta.inject.Singleton;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.fulfilment.PatronRequestView;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

// purpose is to combine patron request and supplier reqeust
@Singleton
public class SupplierRequestService {

	public static final Logger log = LoggerFactory.getLogger(SupplierRequestService.class);
	private final SupplierRequestRepository supplierRequestRepository;

	public SupplierRequestService(SupplierRequestRepository supplierRequestRepository) {
		this.supplierRequestRepository = supplierRequestRepository;
	}

	// method: find supplier request by patron request
	public Mono<PatronRequestView> findSupplierRequest(PatronRequest patronRequest){
		log.debug("findSupplierRequest({})", patronRequest);
		return Mono.just(patronRequest)
				.map(SupplierRequestService::mapToRequestPair)
				.flatMap(this::mapSupplierRequest);
	}

	private Mono<PatronRequestView> mapSupplierRequest(PatronRequestPair patronRequestPair) {
		log.debug("mapSupplierRequest({})", patronRequestPair);
		return Mono.just(patronRequestPair.patronRequest)
			.flatMap(pr -> Flux.from(supplierRequestRepository.findAllByPatronRequest(pr))
			.collectList()
			.map(this::mapToListForPatronRequestView)
				.map(list -> {
					list.forEach( s -> patronRequestPair.patronRequestView.supplierRequests().add(s));
					log.debug("mapSupplierRequest, return({})", patronRequestPair.patronRequestView);
					return patronRequestPair.patronRequestView;
				}));
	}

	private List<PatronRequestView.SupplierRequest> mapToListForPatronRequestView(List<SupplierRequest> supplierRequests) {
		List<PatronRequestView.SupplierRequest> listOfSupplierRequests = new ArrayList<>();
		log.debug("supplierRequests({})", supplierRequests);
		supplierRequests.forEach(sr -> listOfSupplierRequests
				.add( new PatronRequestView.SupplierRequest( sr.getId(),
				new PatronRequestView.Item(sr.getHoldingsItemId()),
				new PatronRequestView.Agency(sr.getHoldingsAgencyCode()))));
		return listOfSupplierRequests;
	}

	private static PatronRequestPair mapToRequestPair(PatronRequest patronRequest) {
		log.debug("mapToPatronRequestView({})", patronRequest);
		return new PatronRequestPair( new PatronRequestView(patronRequest.getId(),
			new PatronRequestView.Citation(patronRequest.getBibClusterId()),
			new PatronRequestView.PickupLocation(patronRequest.getPickupLocationCode()),
			new PatronRequestView.Requestor(patronRequest.getPatronId(),
				new PatronRequestView.Agency(patronRequest.getPatronAgencyCode())),
			new ArrayList<>()), patronRequest);
	}

	private record PatronRequestPair(PatronRequestView patronRequestView, PatronRequest patronRequest) { }
}




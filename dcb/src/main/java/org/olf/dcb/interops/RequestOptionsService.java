package org.olf.dcb.interops;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.RequestOptionsQuery;
import org.olf.dcb.core.interaction.RequestOptionsReport;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Asks a patron request's supplier what DCB's virtual patron may request on the chosen copy.
 * <p>
 * Keyed on DCB's own request id so no patron identifier travels in a URL, and so the identity
 * asked about is the one DCB created rather than one a caller supplies.
 */
@Singleton
public class RequestOptionsService {
	private static final String UNKNOWN_SYSTEM = "unknown";

	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	private final PatronIdentityRepository patronIdentityRepository;
	private final HostLmsService hostLmsService;

	public RequestOptionsService(PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		PatronIdentityRepository patronIdentityRepository, HostLmsService hostLmsService) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.patronIdentityRepository = patronIdentityRepository;
		this.hostLmsService = hostLmsService;
	}

	public Mono<RequestOptionsReport> forPatronRequest(UUID patronRequestId) {
		return Mono.from(patronRequestRepository.findById(patronRequestId))
			.flatMap(this::forActiveSupplierRequest)
			.switchIfEmpty(Mono.fromSupplier(() -> RequestOptionsReport.failed(UNKNOWN_SYSTEM,
				"No patron request " + patronRequestId)));
	}

	private Mono<RequestOptionsReport> forActiveSupplierRequest(PatronRequest patronRequest) {
		return Flux.from(supplierRequestRepository.findAllByPatronRequestAndIsActive(patronRequest, true))
			.next()
			.flatMap(supplierRequest -> ask(patronRequest, supplierRequest))
			.switchIfEmpty(Mono.fromSupplier(() -> RequestOptionsReport.failed(UNKNOWN_SYSTEM,
				"Patron request " + patronRequest.getId() + " has no active supplier request")));
	}

	private Mono<RequestOptionsReport> ask(PatronRequest patronRequest, SupplierRequest supplierRequest) {
		final var system = supplierRequest.getHostLmsCode();

		return virtualPatronIdAt(patronRequest, system)
			.flatMap(patronId -> hostLmsService.getClientFor(system)
				.flatMap(client -> client.checkRequestOptions(new RequestOptionsQuery(patronId,
					supplierRequest.getLocalBibId(), supplierRequest.getLocalHoldingId(),
					supplierRequest.getLocalItemId(), supplierRequest.getLocalItemBarcode()))))
			.switchIfEmpty(Mono.fromSupplier(() -> RequestOptionsReport.failed(system,
				"The patron has no virtual identity at " + system + " yet")))
			.onErrorResume(error -> Mono.just(RequestOptionsReport.failed(system,
				"Could not ask " + system + ": " + error.getMessage())));
	}

	// A failed hold leaves the supplier request without its virtual identity, so the patron's
	// own identities are searched instead. Tens at most: one per system the patron has touched
	private Mono<String> virtualPatronIdAt(PatronRequest patronRequest, String hostLmsCode) {
		return hostLmsService.findByCode(hostLmsCode)
			.map(DataHostLms::getId)
			.flatMap(hostLmsId -> Flux.from(patronIdentityRepository.findAllByPatron(patronRequest.getPatron()))
				.filter(identity -> !Boolean.TRUE.equals(identity.getHomeIdentity()))
				.filter(identity -> hostLmsId.equals(getValueOrNull(identity, PatronIdentity::getHostLms,
					DataHostLms::getId)))
				.next()
				.mapNotNull(PatronIdentity::getLocalId));
	}
}

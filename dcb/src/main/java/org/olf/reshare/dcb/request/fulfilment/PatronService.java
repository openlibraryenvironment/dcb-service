package org.olf.reshare.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;

import java.util.ArrayList;
import java.util.UUID;

import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronIdentityRepository;
import org.olf.reshare.dcb.storage.PatronRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Prototype
public class PatronService {
	private static final Logger log = LoggerFactory.getLogger(PatronService.class);

	private final PatronRepository patronRepository;
	private final PatronIdentityRepository patronIdentityRepository;
	private final HostLmsService hostLmsService;

	public PatronService(PatronRepository patronRepository,
		PatronIdentityRepository patronIdentityRepository, HostLmsService hostLmsService) {

		this.patronRepository = patronRepository;
		this.patronIdentityRepository = patronIdentityRepository;
		this.hostLmsService = hostLmsService;
	}

	public Mono<Patron> findPatronFor(String localSystemCode, String localId) {
		log.debug("FindPatronFor({}, {})", localSystemCode, localId);

		return fetchDataHostLmsByLocalSystemCode(localSystemCode)
			.flatMap(dataHostLms -> fetchPatronIdentityByHomeIdentity(localId, dataHostLms))
			.map(PatronIdentity::getPatron)
			.flatMap(patron -> fetchPatronByPatronId( patron.getId() ))
			// logs that null was returned from the repo (dev purposes only)
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred: {}", error.getMessage());
				return Mono.empty();
			});
	}

	public Mono<Patron> createPatron(String localSystemCode, String localId) {
		log.debug("createPatron({}, {})", localSystemCode, localId);

		return Mono.fromCallable(this::createPatron)
			.flatMap(this::savePatron)
			.flatMap(repoPatron -> fetchDataHostLmsByLocalSystemCode(localSystemCode)
				.flatMap(dataHostLms -> savePatronIdentity(createNewPatronIdentity(repoPatron, dataHostLms, localId))
					.thenReturn(repoPatron)));
	}

	public Mono<PatronRequest> addPatronIdentitiesAndHostLms(PatronRequest patronRequest) {
		log.debug("addPatronIdentitiesAndHostLms({})", patronRequest);

		Patron patron = patronRequest.getPatron();
		return findAllPatronIdentitiesByPatron(patron)
			.flatMap(this::addHostLmsToPatronIdentities)
			.collectList()
			.doOnNext(patron::setPatronIdentities)
			.thenReturn(patronRequest);
	}

	private Mono<PatronIdentity> fetchPatronIdentityByHomeIdentity(
		String localId, DataHostLms hostLms) {

		log.debug("fetchPatronIdentityByHomeLibrary({}, {})", localId, hostLms);

		return Mono.from(patronIdentityRepository
			.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, hostLms, true));
	}

	private Flux<PatronIdentity> findAllPatronIdentitiesByPatron(Patron patron) {
		log.debug("findAllPatronIdentitiesByPatron({})", patron);
		return Flux.from(patronIdentityRepository.findAllByPatron(patron));
	}

	private Mono<PatronIdentity> addHostLmsToPatronIdentities(PatronIdentity patronIdentity) {
		log.debug("addHostLmsToPatronIdentities({})", patronIdentity);
		return Mono.just(patronIdentity)
			.flatMap(this::getHostLmsOfPatronIdentity);
	}

	private Mono<PatronIdentity> getHostLmsOfPatronIdentity(PatronIdentity patronIdentity) {
		log.debug("getHostLmsOfPatronIdentity({})", patronIdentity);
		return fetchDataHostLmsByHostLmsId(patronIdentity.getHostLms().id)
			.doOnNext(patronIdentity::setHostLms)
			.thenReturn(patronIdentity);
	}

	private Patron createPatron() {
		return new Patron(randomUUID(), null, null, new ArrayList<>());
	}

	private PatronIdentity createNewPatronIdentity(Patron patron, DataHostLms dataHostLms,
		String localPatronIdentifier) {

		return new PatronIdentity(randomUUID(), null, null, patron, dataHostLms,
			localPatronIdentifier, true);
	}

	private Mono<DataHostLms> fetchDataHostLmsByLocalSystemCode(String localSystemCode) {
		return Mono.from(hostLmsService.findByCode(localSystemCode));
	}

	private Mono<DataHostLms> fetchDataHostLmsByHostLmsId(UUID hostLmsId) {
		return Mono.from(hostLmsService.findById(hostLmsId));
	}

	private Mono<PatronIdentity> savePatronIdentity(PatronIdentity patronIdentity) {
		return Mono.from(patronIdentityRepository.save(patronIdentity));
	}

	private Mono<Patron> savePatron(Patron patron) {
		return Mono.from(patronRepository.save(patron));
	}

	private Mono<Patron> fetchPatronByPatronId(UUID patronId) {
		return Mono.from(patronRepository.findById(patronId));
	}
}

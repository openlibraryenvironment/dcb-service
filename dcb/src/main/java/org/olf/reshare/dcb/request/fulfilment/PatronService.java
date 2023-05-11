package org.olf.reshare.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
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
			.flatMap(patron -> findById(patron.getId()))
			// logs that null was returned from the repo (dev purposes only)
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred: {}", error.getMessage());
				return Mono.empty();
			});
	}

	public Mono<Patron> findById(UUID patronId) {
		return Mono.from(patronRepository.findById(patronId))
			.zipWhen(patron -> findAllPatronIdentitiesByPatron(patron).collectList(),
				this::addIdentities);
	}

	public Mono<Patron> createPatron(String localSystemCode, String localId,
		String homeLibraryCode) {

		log.debug("createPatron({}, {}, {})", localSystemCode, localId, homeLibraryCode);

		return savePatron(createPatron(homeLibraryCode))
			.flatMap(patron -> savePatronIdentity(patron, localSystemCode, localId))
			.flatMap(patronIdentity -> findById(patronIdentity.getPatron().getId()));
	}

	private Mono<PatronIdentity> fetchPatronIdentityByHomeIdentity(
		String localId, DataHostLms hostLms) {

		log.debug("fetchPatronIdentityByHomeLibrary({}, {})", localId, hostLms);

		return Mono.from(patronIdentityRepository
			.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, hostLms, true));
	}

	private Flux<PatronIdentity> findAllPatronIdentitiesByPatron(Patron patron) {
		log.debug("findAllPatronIdentitiesByPatron({})", patron);

		return Flux.from(patronIdentityRepository.findAllByPatron(patron))
			.flatMap(this::addHostLmsToPatronIdentities);
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

	private Patron createPatron(String homeLibraryCode) {
		return new Patron(randomUUID(), null, null,
			homeLibraryCode, new ArrayList<>());
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

	private Mono<Patron> savePatron(Patron patron) {
		return Mono.from(patronRepository.save(patron));
	}

	private Mono<PatronIdentity> savePatronIdentity(Patron patron,
		String localSystemCode, String localId) {

		return fetchDataHostLmsByLocalSystemCode(localSystemCode)
			.flatMap(hostLms -> savePatronIdentity(patron, localId, hostLms));
	}

	private Mono<PatronIdentity> savePatronIdentity(Patron patron, String localId,
		DataHostLms hostLms) {

		return savePatronIdentity(createNewPatronIdentity(
			// Patron associated with an identity has to be shallow, to avoid a circular loop
			createPatronWithOnlyId(patron.getId()), hostLms, localId));
	}

	private Mono<PatronIdentity> savePatronIdentity(PatronIdentity patronIdentity) {
		return Mono.from(patronIdentityRepository.save(patronIdentity));
	}

	private static Patron createPatronWithOnlyId(UUID id) {
		return Patron.builder()
			.id(id)
			.build();
	}

	private Patron addIdentities(Patron patron, List<PatronIdentity> identities) {
		log.debug("addIdentities({}, {})", patron, identities);

		patron.setPatronIdentities(identities);

		return patron;
	}
}

package org.olf.reshare.dcb.request.fulfilment;

import jakarta.inject.Singleton;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import org.olf.reshare.dcb.storage.PatronIdentityRepository;
import org.olf.reshare.dcb.storage.PatronRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

import static java.util.UUID.randomUUID;

@Singleton
public class PatronService {
	private static final Logger log = LoggerFactory.getLogger(PatronService.class);
	private final PatronRepository patronRepository;
	private final PatronIdentityRepository patronIdentityRepository;
	private final HostLmsRepository hostLmsRepository;
	public PatronService(PatronRepository patronRepository, PatronIdentityRepository patronIdentityRepository,
		HostLmsRepository hostLmsRepository) {
		this.patronRepository = patronRepository;
		this.patronIdentityRepository = patronIdentityRepository;
		this.hostLmsRepository = hostLmsRepository;
	}

	public Mono<Patron> getOrCreatePatronForRequestor(PlacePatronRequestCommand placePatronRequestCommand) {
		log.debug("getOrCreatePatronForRequestor({})", placePatronRequestCommand);
		return findPatronFor(placePatronRequestCommand.requestor())
			// Continue with the found patron
			.flatMap(Mono::just)
			// Create a new patron if not found
			.switchIfEmpty(Mono.defer(() -> createPatronFor(placePatronRequestCommand.requestor())));
	}

	public Mono<Patron> findPatronFor(PlacePatronRequestCommand.Requestor requestor) {
		log.debug("findPatronFor({})", requestor);

		final String localId = requestor.localId();
		final String localSystemCode = requestor.localSystemCode();

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

	private Mono<PatronIdentity> fetchPatronIdentityByHomeIdentity(String localId, DataHostLms hostLms) {
		log.debug("fetchPatronIdentityByHomeLibrary({}, {})", localId, hostLms);
		return Mono.from(patronIdentityRepository.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, hostLms, true));
	}

	public Mono<Patron> createPatronFor(PlacePatronRequestCommand.Requestor requestor) {
		log.debug("createPatronFor({})", requestor);

		final String localSystemCode = requestor.localSystemCode();
		final String localId = requestor.localId();

		return Mono.fromCallable(this::createNewPatron)
			.flatMap(this::savePatron)
			.flatMap(repoPatron -> fetchDataHostLmsByLocalSystemCode( localSystemCode )
				.flatMap(dataHostLms -> savePatronIdentity( createNewPatronIdentity(repoPatron, dataHostLms, localId) )
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


	private Patron createNewPatron() { return new Patron(randomUUID(), null, null, new ArrayList<>());}
	private PatronIdentity createNewPatronIdentity(Patron patron, DataHostLms dataHostLms, String localPatronIdentifier) {
		return new PatronIdentity(randomUUID(), null, null, patron, dataHostLms, localPatronIdentifier, true);
	}
	public Mono<DataHostLms> fetchDataHostLmsByLocalSystemCode(String localSystemCode) { return Mono.from(hostLmsRepository.findByCode(localSystemCode));}
	public Mono<DataHostLms> fetchDataHostLmsByHostLmsId(UUID hostLmsId) { return Mono.from(hostLmsRepository.findById(hostLmsId)); }
	private Mono<PatronIdentity> savePatronIdentity(PatronIdentity patronIdentity) { return Mono.from(patronIdentityRepository.save(patronIdentity)); }
	private Mono<Patron> savePatron(Patron patron) { return Mono.from(patronRepository.save(patron)); }
	private Mono<Patron> fetchPatronByPatronId(UUID patronId) { return Mono.from(patronRepository.findById(patronId)); }

}

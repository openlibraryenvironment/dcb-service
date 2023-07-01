package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.storage.PatronIdentityRepository;
import org.olf.reshare.dcb.storage.PatronRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.UUID.randomUUID;
import static lombok.AccessLevel.PACKAGE;

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

	public Mono<PatronId> findPatronFor(String localSystemCode, String localId) {
		// log.debug("findPatronFor({}, {})", localSystemCode, localId);

		return fetchDataHostLmsByLocalSystemCode(localSystemCode)
			.flatMap(hostLms -> fetchPatronIdentityByHomeIdentity(localId, hostLms))
			.map(PatronId::fromIdentity);
	}

	public Mono<Patron> findById(PatronId patronId) {
		// log.debug("findById({})", patronId);

		return Mono.from(patronRepository.findById(patronId.getValue()))
			.zipWhen(this::fetchAllIdentities, this::addIdentities);
	}

	public Mono<PatronId> createPatron(String localSystemCode, String localId,
		String homeLibraryCode) {

		// log.debug("createPatron({}, {}, {})", localSystemCode, localId, homeLibraryCode);

		return savePatron(createPatron(homeLibraryCode))
			.flatMap(patron -> savePatronIdentity(patron, localSystemCode, localId))
			.map(PatronId::fromIdentity);
	}

	private Mono<PatronIdentity> fetchPatronIdentityByHomeIdentity(
		String localId, DataHostLms hostLms) {

		// log.debug("fetchPatronIdentityByHomeLibrary({}, {})", localId, hostLms);

		return Mono.from(patronIdentityRepository
			.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, hostLms, true));
	}

	private Flux<PatronIdentity> findAllPatronIdentitiesByPatron(Patron patron) {
		// log.debug("findAllPatronIdentitiesByPatron({})", patron);

		return Flux.from(patronIdentityRepository.findAllByPatron(patron))
			.flatMap(this::addHostLmsToPatronIdentities);
	}

	private Mono<PatronIdentity> addHostLmsToPatronIdentities(PatronIdentity patronIdentity) {
		// log.debug("addHostLmsToPatronIdentities({})", patronIdentity);

		return Mono.just(patronIdentity)
			.flatMap(this::getHostLmsOfPatronIdentity);
	}

	private Mono<PatronIdentity> getHostLmsOfPatronIdentity(PatronIdentity patronIdentity) {
		// log.debug("getHostLmsOfPatronIdentity({})", patronIdentity);

		return fetchDataHostLmsByHostLmsId(patronIdentity.getHostLms().getId())
			.doOnNext(patronIdentity::setHostLms)
			.thenReturn(patronIdentity);
	}

	private Patron createPatron(String homeLibraryCode) {
		return new Patron(randomUUID(), null, null,
			homeLibraryCode, new ArrayList<>());
	}

	public PatronIdentity createNewPatronIdentity(Patron patron, DataHostLms dataHostLms,
		String localPatronIdentifier, Boolean homeIdentity) {

		log.debug("createPatronIdentity({}, {}, {}, {})", patron, dataHostLms, localPatronIdentifier, homeIdentity);

		final var result = PatronIdentity.builder()
			.id(randomUUID())
			.patron(patron)
			.hostLms(dataHostLms)
			.localId(localPatronIdentifier)
			.homeIdentity(homeIdentity)
			.build();

		log.debug("result of create new patronIdentity: {}", result);
		return result;
	}

	public Mono<PatronIdentity> createPatronIdentity(Patron patron, String localId, String hostLmsCode,
		Boolean homeIdentity) {

		log.debug("createPatronIdentity({}, {}, {}, {})", patron, hostLmsCode, localId, homeIdentity);

		return fetchDataHostLmsByLocalSystemCode(hostLmsCode)
			.map(dataHostLms -> createNewPatronIdentity(patron, dataHostLms, localId, homeIdentity))
			.flatMap(this::savePatronIdentity);
	}

	private Mono<DataHostLms> fetchDataHostLmsByLocalSystemCode(String localSystemCode) {
		return Mono.from(hostLmsService.findByCode(localSystemCode));
	}

	private Mono<DataHostLms> fetchDataHostLmsByHostLmsId(UUID hostLmsId) {
		return Mono.from(hostLmsService.findById(hostLmsId));
	}

	private Mono<PatronIdentity> savePatronIdentity(Patron patron,
		String localSystemCode, String localId) {

		log.debug("savePatronIdentity({},{},{})",patron,localSystemCode,localId);

		return fetchDataHostLmsByLocalSystemCode(localSystemCode)
			.flatMap(hostLms -> savePatronIdentity(patron, localId, hostLms));
	}

	private Mono<PatronIdentity> savePatronIdentity(Patron patron, String localId,
		DataHostLms hostLms) {

		return savePatronIdentity(createNewPatronIdentity(
			// Patron associated with an identity has to be shallow, to avoid a circular loop
			createPatronWithOnlyId(patron.getId()), hostLms, localId, true));
	}

	private Mono<PatronIdentity> savePatronIdentity(PatronIdentity patronIdentity) {
		return Mono.from(patronIdentityRepository.save(patronIdentity));
	}

	private Mono<Patron> savePatron(Patron patron) {
		return Mono.from(patronRepository.save(patron));
	}

	private static Patron createPatronWithOnlyId(UUID id) {
		return Patron.builder()
			.id(id)
			.build();
	}

	private Patron addIdentities(Patron patron, List<PatronIdentity> identities) {
		// log.debug("addIdentities({}, {})", patron, identities);

		patron.setPatronIdentities(identities);

		return patron;
	}
	
	public String getUniqueIdStringFor(Patron patron) {
		return patron.getPatronIdentities()
			.stream()
			.filter(PatronIdentity::getHomeIdentity)
			.map(pi -> pi.getLocalId() + "@" + patron.getHomeLibraryCode())
			.collect(Collectors.joining());
	}

	public Optional<PatronIdentity> findIdentityByLocalId(Patron patron, String localId) {
		return patron.getPatronIdentities()
			.stream()
			.filter(pi -> pi.getLocalId().equals(localId))
			.findFirst();
	}

	public Mono<PatronIdentity> checkForPatronIdentity(Patron patron, String hostLmsCode, String localId) {
		// log.debug("checkForPatronIdentity {}, {}, {}", patron.getId(), hostLmsCode, localId);

		return Mono.justOrEmpty(findIdentityByLocalId(patron, localId))
			.switchIfEmpty(Mono.defer(() -> createPatronIdentity(patron, localId, hostLmsCode, false)));
	}

	private Mono<List<PatronIdentity>> fetchAllIdentities(Patron patron) {
		return findAllPatronIdentitiesByPatron(patron).collectList();
	}

        public Mono<PatronIdentity> getPatronIdentityById(UUID id) {
                return Mono.from(patronIdentityRepository.findById(id));
        }

	@Value
	@RequiredArgsConstructor(access = PACKAGE)
	public static class PatronId {
		UUID value;

		static PatronId fromIdentity(PatronIdentity identity) {
			return fromPatron(identity.getPatron());
		}

		static PatronId fromPatron(Patron patron) {
			return new PatronId(patron.getId());
		}
	}
}

package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static lombok.AccessLevel.PACKAGE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.PatronRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Prototype
public class PatronService {
	private static final Logger log = LoggerFactory.getLogger(PatronService.class);

	private final PatronRepository patronRepository;
	private final PatronIdentityRepository patronIdentityRepository;
	private final HostLmsService hostLmsService;
	private final AgencyRepository agencyRepository;

	public PatronService(PatronRepository patronRepository, PatronIdentityRepository patronIdentityRepository,
			HostLmsService hostLmsService, AgencyRepository agencyRepository) {

		this.patronRepository = patronRepository;
		this.patronIdentityRepository = patronIdentityRepository;
		this.hostLmsService = hostLmsService;
		this.agencyRepository = agencyRepository;
	}

	public Mono<PatronId> findPatronFor(String localSystemCode, String localId) {
		// log.debug("findPatronFor({}, {})", localSystemCode, localId);

		return fetchDataHostLmsByLocalSystemCode(localSystemCode)
				.flatMap(hostLms -> fetchPatronIdentityByHomeIdentity(localId, hostLms)).map(PatronId::fromIdentity);
	}

	public Mono<Patron> findById(PatronId patronId) {
		// log.debug("findById({})", patronId);

		return Mono.from(patronRepository.findById(patronId.getValue())).zipWhen(this::fetchAllIdentities,
				this::addIdentities);
	}

	public Mono<PatronId> createPatron(String localSystemCode, String localId, String homeLibraryCode) {

		// log.debug("createPatron({}, {}, {})", localSystemCode, localId,
		// homeLibraryCode);

		return savePatron(createPatron(homeLibraryCode))
				.flatMap(patron -> savePatronIdentity(patron, localSystemCode, localId)).map(PatronId::fromIdentity);
	}

	private Mono<PatronIdentity> fetchPatronIdentityByHomeIdentity(String localId, DataHostLms hostLms) {

		// log.debug("fetchPatronIdentityByHomeLibrary({}, {})", localId, hostLms);

		return Mono.from(patronIdentityRepository.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, hostLms, true));
	}

	public Flux<PatronIdentity> findAllPatronIdentitiesByPatron(Patron patron) {
		// log.debug("findAllPatronIdentitiesByPatron({})", patron);

		return Flux.from(patronIdentityRepository.findAllByPatron(patron))
                                .flatMap(this::addHostLmsToPatronIdentity)
				.flatMap(this::addResolvedAgencyToPatronIdentity);
	}

	private Mono<PatronIdentity> addHostLmsToPatronIdentity(PatronIdentity patronIdentity) {
		// log.debug("addHostLmsToPatronIdentity({})", patronIdentity);

		return Mono.just(patronIdentity).flatMap(this::getHostLmsOfPatronIdentity);
	}

	private Mono<PatronIdentity> getHostLmsOfPatronIdentity(PatronIdentity patronIdentity) {
		// log.debug("getHostLmsOfPatronIdentity({})", patronIdentity);

		return hostLmsService.findById(patronIdentity.getHostLms().getId()).doOnNext(patronIdentity::setHostLms)
				.thenReturn(patronIdentity);
	}

	private Mono<PatronIdentity> addResolvedAgencyToPatronIdentity(PatronIdentity patronIdentity) {

		// Exit early if there is no attached resolved agency - we generally only set
		// resolvedAgency for "Home" identities
		if (patronIdentity.getResolvedAgency() == null)
			return Mono.just(patronIdentity);

		return Mono.from( agencyRepository.findById(patronIdentity.getResolvedAgency().getId()) )
				.map(patronIdentity::setResolvedAgency)
                                .defaultIfEmpty(patronIdentity);
	}

	private Patron createPatron(String homeLibraryCode) {
		return new Patron(randomUUID(), null, null, homeLibraryCode, new ArrayList<>());
	}

	public PatronIdentity createNewPatronIdentity(Patron patron, DataHostLms dataHostLms, String localPatronIdentifier,
			String localPtype, Boolean homeIdentity) {

		log.debug("createPatronIdentity({}, {}, {}, {})", patron, dataHostLms, localPatronIdentifier, homeIdentity);
                String local_barcode = null;

		final var result = PatronIdentity.builder().id(randomUUID()).patron(patron).hostLms(dataHostLms)
				.localId(localPatronIdentifier).localPtype(localPtype).homeIdentity(homeIdentity)
                                .localBarcode(local_barcode)
                                .build();

		log.debug("result of create new patronIdentity: {}", result);
		return result;
	}

	public Mono<PatronIdentity> createPatronIdentity(Patron patron, String localId, String localPType, String hostLmsCode,
			Boolean homeIdentity) {

		log.debug("createPatronIdentity({}, {}, {}, {})", patron, hostLmsCode, localId, homeIdentity);

		return fetchDataHostLmsByLocalSystemCode(hostLmsCode)
				.map(dataHostLms -> createNewPatronIdentity(patron, dataHostLms, localId, localPType, homeIdentity))
				.flatMap(this::savePatronIdentity);
	}

	private Mono<DataHostLms> fetchDataHostLmsByLocalSystemCode(String localSystemCode) {
		return Mono.from(hostLmsService.findByCode(localSystemCode));
	}

	private Mono<PatronIdentity> savePatronIdentity(Patron patron, String localSystemCode, String localId) {

		log.debug("savePatronIdentity({},{},{})", patron, localSystemCode, localId);

		return fetchDataHostLmsByLocalSystemCode(localSystemCode)
				.flatMap(hostLms -> savePatronIdentity(patron, localId, hostLms));
	}

	private Mono<PatronIdentity> savePatronIdentity(Patron patron, String localId, DataHostLms hostLms) {

		return savePatronIdentity(createNewPatronIdentity(
				// Patron associated with an identity has to be shallow, to avoid a circular
				// loop
				createPatronWithOnlyId(patron.getId()), hostLms, localId, null, true));
	}

	private Mono<PatronIdentity> savePatronIdentity(PatronIdentity patronIdentity) {
		return Mono.from(patronIdentityRepository.save(patronIdentity));
	}

	private Mono<Patron> savePatron(Patron patron) {
		return Mono.from(patronRepository.save(patron));
	}

	private static Patron createPatronWithOnlyId(UUID id) {
		return Patron.builder().id(id).build();
	}

	private Patron addIdentities(Patron patron, List<PatronIdentity> identities) {
		// log.debug("addIdentities({}, {})", patron, identities);

		patron.setPatronIdentities(identities);

		return patron;
	}

	public String getUniqueIdStringFor(Patron patron) {
		return patron.getPatronIdentities().stream().filter(PatronIdentity::getHomeIdentity).map(pi -> {
			if (pi.getResolvedAgency() == null)
				throw new RuntimeException("No resolved agency for patron " + patron.getId() + "homeLibraryCode was "+patron.getHomeLibraryCode());
			return pi.getLocalId() + "@" + pi.getResolvedAgency().getCode();
		}).collect(Collectors.joining());
	}

	public Optional<PatronIdentity> findIdentityByLocalId(Patron patron, String localId) {
		return patron.getPatronIdentities().stream().filter(pi -> pi.getLocalId().equals(localId)).findFirst();
	}

	public Mono<PatronIdentity> checkForPatronIdentity(Patron patron, String hostLmsCode, String localId,
			String localPType) {
		log.debug("checkForPatronIdentity {}, {}, {}", patron.getId(), hostLmsCode, localId);

		return Mono.justOrEmpty(findIdentityByLocalId(patron, localId))
				.switchIfEmpty(Mono.defer(() -> createPatronIdentity(patron, localId, localPType, hostLmsCode, false)));
	}

	private Mono<List<PatronIdentity>> fetchAllIdentities(Patron patron) {
		return findAllPatronIdentitiesByPatron(patron).collectList();
	}

	public Mono<PatronIdentity> getPatronIdentityById(UUID id) {
		return Mono.from(patronIdentityRepository.findById(id))
                                .flatMap(this::addHostLmsToPatronIdentity)
                                .flatMap(this::addResolvedAgencyToPatronIdentity);

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

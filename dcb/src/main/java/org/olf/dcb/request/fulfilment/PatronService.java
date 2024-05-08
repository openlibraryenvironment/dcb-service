package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static lombok.AccessLevel.PACKAGE;

import java.util.*;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.PatronRepository;

import io.micronaut.context.annotation.Prototype;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class PatronService {
	private final PatronRepository patronRepository;
	private final PatronIdentityRepository patronIdentityRepository;
	private final HostLmsService hostLmsService;
	private final AgencyService agencyService;

	public PatronService(PatronRepository patronRepository,
		PatronIdentityRepository patronIdentityRepository,
		HostLmsService hostLmsService, AgencyService agencyService) {

		this.patronRepository = patronRepository;
		this.patronIdentityRepository = patronIdentityRepository;
		this.hostLmsService = hostLmsService;
		this.agencyService = agencyService;
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

	public Mono<PatronId> createPatron(String localSystemCode, String localId, String homeLibraryCode) {
		// log.debug("createPatron({}, {}, {})", localSystemCode, localId, homeLibraryCode);

		return savePatron(createPatron(homeLibraryCode))
			.flatMap(patron -> savePatronIdentity(patron, localSystemCode, localId))
			.map(PatronId::fromIdentity);
	}

	private Mono<PatronIdentity> fetchPatronIdentityByHomeIdentity(String localId, DataHostLms hostLms) {
		// log.debug("fetchPatronIdentityByHomeLibrary({}, {})", localId, hostLms);

		return Mono.from(patronIdentityRepository.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, hostLms, true));
	}

	public Flux<PatronIdentity> findAllPatronIdentitiesByPatron(Patron patron) {
		 log.debug("findAllPatronIdentitiesByPatron({})", patron);

		return Flux.from(patronIdentityRepository.findAllByPatron(patron))
			.flatMap(this::addHostLmsToPatronIdentity)
			.flatMap(this::addResolvedAgencyToPatronIdentity);
	}

	private Mono<PatronIdentity> addHostLmsToPatronIdentity(PatronIdentity patronIdentity) {
		// log.debug("getHostLmsOfPatronIdentity({})", patronIdentity);

		if (patronIdentity.getHostLms() == null || patronIdentity.getHostLms().getId() == null) {
			return Mono.just(patronIdentity);
		}

		return hostLmsService.findById(patronIdentity.getHostLms().getId())
			.map(patronIdentity::setHostLms)
			.defaultIfEmpty(patronIdentity);
	}

	private Mono<PatronIdentity> addResolvedAgencyToPatronIdentity(PatronIdentity patronIdentity) {
		// log.debug("addResolvedAgencyToPatronIdentity({})", patronIdentity);

		// Exit early if there is no attached resolved agency - we generally only set
		// resolvedAgency for "Home" identities
		if (patronIdentity.getResolvedAgency() == null || patronIdentity.getResolvedAgency().getId() == null)
			return Mono.just(patronIdentity);

		return agencyService.findById(patronIdentity.getResolvedAgency().getId())
			.map(patronIdentity::setResolvedAgency)
			.defaultIfEmpty(patronIdentity);
	}

	private Patron createPatron(String homeLibraryCode) {
		return new Patron(randomUUID(), null, null, homeLibraryCode, new ArrayList<>());
	}

	public PatronIdentity createNewPatronIdentity(Patron patron, DataHostLms dataHostLms, String localPatronIdentifier,
			String localPtype, Boolean homeIdentity, String barcode) {

		log.debug("createPatronIdentity({}, {}, {}, {})", patron, dataHostLms, localPatronIdentifier, homeIdentity);

		final var result = PatronIdentity.builder()
			.id(randomUUID())
			.patron(patron)
			.hostLms(dataHostLms)
			.localId(localPatronIdentifier)
			.localPtype(localPtype)
			.homeIdentity(homeIdentity)
			.localBarcode(barcode)
			.build();

		log.debug("result of create new patronIdentity: {}", result);
		return result;
	}

	public Mono<PatronIdentity> createPatronIdentity(Patron patron, String localId, String localPType, String hostLmsCode,
			Boolean homeIdentity, String barcode) {

		log.debug("createPatronIdentity({}, {}, {}, {})", patron, hostLmsCode, localId, homeIdentity);

		return fetchDataHostLmsByLocalSystemCode(hostLmsCode)
				.map(dataHostLms -> createNewPatronIdentity(patron, dataHostLms, localId, localPType, homeIdentity, barcode))
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
				createPatronWithOnlyId(patron.getId()), hostLms, localId, null, true, null));
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

	public PatronIdentity findIdentityByLocalId(List<PatronIdentity> identities, String localId) {

		if (identities == null || identities.isEmpty()) {
			return null;
		}

		return identities.stream()
			.filter(pi -> pi != null && localId.equals(pi.getLocalId()))
			.findFirst()
			.orElse(null); // to trigger the switch
	}

	public Mono<PatronIdentity> checkForPatronIdentity(Patron patron, String hostLmsCode, String localId,
			String localPType, String barcode) {

		log.debug("checkForPatronIdentity {}, {}, {}, {}, {}",
			patron, hostLmsCode, localId, localPType, barcode);

		return getPatronIdentitiesBy(patron)
			.flatMap(identities -> Mono.justOrEmpty(findIdentityByLocalId(identities, localId)))
			.switchIfEmpty(Mono.defer(() -> createPatronIdentity(patron, localId, localPType, hostLmsCode, false, barcode)));
	}

	private Mono<List<PatronIdentity>> getPatronIdentitiesBy(Patron patron) {

		final var identities = identitiesOrEmpty(patron);

		return Mono.justOrEmpty(identities)
			.switchIfEmpty(Mono.defer(() -> fetchAllIdentities(patron)));
	}

	private static List<PatronIdentity> identitiesOrEmpty(Patron patron) {
		return patron.getPatronIdentities() == null || patron.getPatronIdentities().isEmpty()
			? null
			: patron.getPatronIdentities();
	}

	private Mono<List<PatronIdentity>> fetchAllIdentities(Patron patron) {
		return findAllPatronIdentitiesByPatron(patron).collectList();
	}

	public Mono<PatronIdentity> getPatronIdentityById(UUID id) {
		return Mono.from(patronIdentityRepository.findById(id))
			.flatMap(this::addHostLmsToPatronIdentity)
			.flatMap(this::addResolvedAgencyToPatronIdentity);
	}

	public Mono<DataAgency> findResolvedAgencyByIdentity(PatronIdentity identity) {
		return Mono.from(patronIdentityRepository.findResolvedAgencyById(identity.getId()));
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

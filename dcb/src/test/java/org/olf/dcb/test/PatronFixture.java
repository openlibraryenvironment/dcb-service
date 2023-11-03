package org.olf.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.PatronRepository;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class PatronFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final PatronRepository patronRepository;
	private final PatronIdentityRepository patronIdentityRepository;
	private final HostLmsService hostLmsService;
	private final PatronRequestsFixture patronRequestsFixture;

	PatronFixture(PatronRepository patronRepository,
		PatronIdentityRepository patronIdentityRepository,
		HostLmsService hostLmsService, PatronRequestsFixture patronRequestsFixture) {

		this.patronRepository = patronRepository;
		this.patronIdentityRepository = patronIdentityRepository;
		this.hostLmsService = hostLmsService;
		this.patronRequestsFixture = patronRequestsFixture;
	}

	public Patron savePatron(String homeLibraryCode) {
		return savePatron(Patron.builder()
			.id(randomUUID())
			.homeLibraryCode(homeLibraryCode)
			.build());
	}

	public Patron savePatron(Patron patron) {
		return singleValueFrom(patronRepository.save(patron));
	}

	public void saveIdentity(Patron patron, DataHostLms homeHostLms,
		String localId, boolean homeIdentity, String localPtype, String localHomeLibraryCode, DataAgency resolvedAgency) {

                PatronIdentity pi = PatronIdentity.builder()
			.id(randomUUID())
			.patron(patron)
			.localId(localId)
			.hostLms(homeHostLms)
			.homeIdentity(homeIdentity)
			.localPtype(localPtype)
			.localHomeLibraryCode(localHomeLibraryCode)
			.resolvedAgency(resolvedAgency)
			.localBarcode("8675309012")
			.build();

		saveIdentity(pi);
	}

	private void saveIdentity(PatronIdentity identity) {
		singleValueFrom(patronIdentityRepository.save(identity));
	}

	public void deleteAllPatrons() {
		patronRequestsFixture.deleteAll();

		deleteAllPatronIdentities();

		dataAccess.deleteAll(patronRepository.queryAll(),
			patronIdentity -> patronRepository.delete(patronIdentity.getId()));
	}

	public Patron findPatron(String localSystemCode, String localId) {
		return Mono.from(hostLmsService.findByCode(localSystemCode))
			.flatMap(hostLms -> Mono.from(patronIdentityRepository
				.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, hostLms, true)))
			.flatMap(identity -> Mono.from(
				patronRepository.findById(identity.getPatron().getId())))
			.block();
	}

	public List<Patron> findAll() {
		return manyValuesFrom(patronRepository.queryAll());
	}

	public List<PatronIdentity> findIdentities(Patron patron) {
		return manyValuesFrom(patronIdentityRepository.findAllByPatron(patron));
	}

	private void deleteAllPatronIdentities() {
		dataAccess.deleteAll(patronIdentityRepository.queryAll(),
			patronIdentity -> patronIdentityRepository.delete(patronIdentity.getId()));
	}
}

package org.olf.reshare.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.reshare.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.reshare.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.storage.PatronIdentityRepository;
import org.olf.reshare.dcb.storage.PatronRepository;

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
		return savePatron(
			Patron.builder()
				.id(randomUUID())
				.homeLibraryCode(homeLibraryCode)
				.build());
	}

	public Patron savePatron(Patron patron) {
		return Mono.from(patronRepository.save(patron)).block();
	}

	public void saveHomeIdentity(Patron patron, DataHostLms homeHostLms, String localId) {
		singleValueFrom(patronIdentityRepository.save(
			PatronIdentity.builder()
				.id(randomUUID())
				.patron(patron)
				.localId(localId)
				.hostLms(homeHostLms)
				.homeIdentity(true)
				.build()));
	}

	public void deleteAllPatrons() {
		patronRequestsFixture.deleteAllPatronRequests();

		deleteAllPatronIdentities();

		dataAccess.deleteAll(patronRepository.findAll(),
			patronIdentity -> patronRepository.delete(patronIdentity.getId()));
	}

	void deleteAllPatronIdentities() {
		dataAccess.deleteAll(patronIdentityRepository.findAll(),
			patronIdentity -> patronIdentityRepository.delete(patronIdentity.getId()));
	}

	public Patron findPatron(String localSystemCode, String localId) {
		return Mono.from(hostLmsService.findByCode(localSystemCode))
			.flatMap(hostLms -> Mono.from(patronIdentityRepository
				.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, hostLms, true)))
			.flatMap(identity -> Mono.from(
				patronRepository.findById(identity.getPatron().getId())))
			.block();
	}

	public List<PatronIdentity> findIdentities(Patron patron) {
		return manyValuesFrom(patronIdentityRepository.findAllByPatron(patron));
	}

	public List<Patron> findAll() {
		return manyValuesFrom(patronRepository.findAll());
	}
}

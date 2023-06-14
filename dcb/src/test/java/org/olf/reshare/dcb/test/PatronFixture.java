package org.olf.reshare.dcb.test;

import static java.time.Instant.now;
import static org.olf.reshare.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.storage.PatronIdentityRepository;
import org.olf.reshare.dcb.storage.PatronRepository;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class PatronFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final PatronRepository patronRepository;
	private final PatronIdentityRepository patronIdentityRepository;
	private final PatronRequestsFixture patronRequestsFixture;

	PatronFixture(PatronRepository patronRepository,
		PatronIdentityRepository patronIdentityRepository,
		PatronRequestsFixture patronRequestsFixture) {

		this.patronRepository = patronRepository;
		this.patronIdentityRepository = patronIdentityRepository;
		this.patronRequestsFixture = patronRequestsFixture;
	}

	public Patron savePatron(UUID patronId, String homeLibCode) {
		return singleValueFrom(patronRepository.save(
				Patron.builder()
					.id(patronId)
					.dateCreated(now())
					.dateUpdated(now())
					.homeLibraryCode(homeLibCode)
					.build()));
	}

	public void saveHomeIdentity(UUID patronIdentityId, Patron patron,
		String homeLibCode, DataHostLms hostLms) {

		singleValueFrom(patronIdentityRepository.save(
			PatronIdentity.builder()
				.id(patronIdentityId)
				.dateCreated(now())
				.dateUpdated(now())
				.patron(patron)
				.localId(homeLibCode)
				.hostLms(hostLms)
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
}

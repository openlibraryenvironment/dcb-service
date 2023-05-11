package org.olf.reshare.dcb.test;

import org.olf.reshare.dcb.storage.PatronRepository;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class PatronFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final PatronRepository patronRepository;
	private final PatronIdentityFixture patronIdentityFixture;
	private final PatronRequestsFixture patronRequestsFixture;

	PatronFixture(PatronRepository patronRepository,
		PatronIdentityFixture patronIdentityFixture,
		PatronRequestsFixture patronRequestsFixture) {

		this.patronRepository = patronRepository;
		this.patronIdentityFixture = patronIdentityFixture;
		this.patronRequestsFixture = patronRequestsFixture;
	}

	public void deleteAllPatrons() {
		patronRequestsFixture.deleteAllPatronRequests();

		patronIdentityFixture.deleteAllPatronIdentities();

		dataAccess.deleteAll(patronRepository.findAll(),
			patronIdentity -> patronRepository.delete(patronIdentity.getId()));
	}
}

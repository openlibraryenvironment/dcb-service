package org.olf.reshare.dcb.test;

import org.olf.reshare.dcb.storage.PatronIdentityRepository;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class PatronIdentityFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final PatronIdentityRepository patronIdentityRepository;

	PatronIdentityFixture(PatronIdentityRepository patronIdentityRepository) {
		this.patronIdentityRepository = patronIdentityRepository;
	}

	public void deleteAllPatronIdentities() {
		dataAccess.deleteAll(patronIdentityRepository.findAll(),
			patronIdentity -> patronIdentityRepository.delete(patronIdentity.getId()));
	}
}

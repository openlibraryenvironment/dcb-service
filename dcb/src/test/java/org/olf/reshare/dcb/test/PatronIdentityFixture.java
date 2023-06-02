package org.olf.reshare.dcb.test;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.storage.PatronIdentityRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static java.time.Instant.now;

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

	public void saveHomeIdentity(UUID patronIdentityId, Patron patron, String homeLibCode, DataHostLms hostLms) {
		Mono.from(patronIdentityRepository.save(
				PatronIdentity
					.builder()
					.id(patronIdentityId)
					.dateCreated(now())
					.dateUpdated(now())
					.patron(patron)
					.localId(homeLibCode)
					.hostLms(hostLms)
					.homeIdentity(true)
					.build()
			))
			.block();
	}
}

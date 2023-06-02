package org.olf.reshare.dcb.test;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.storage.PatronRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static java.time.Instant.now;

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
	public Patron savePatron(UUID patronId, String homeLibCode) {
		return Mono.from(patronRepository.save(
				Patron
					.builder()
					.id(patronId)
					.dateCreated(now())
					.dateUpdated(now())
					.homeLibraryCode(homeLibCode)
					.build()
			))
			.block();
	}

	public void deleteAllPatrons() {
		patronRequestsFixture.deleteAllPatronRequests();

		patronIdentityFixture.deleteAllPatronIdentities();

		dataAccess.deleteAll(patronRepository.findAll(),
			patronIdentity -> patronRepository.delete(patronIdentity.getId()));
	}
}

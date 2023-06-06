package org.olf.reshare.dcb.test;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Prototype
public class HostLmsFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final HostLmsRepository hostLmsRepository;

	private final PatronIdentityFixture patronIdentityFixture;

	public HostLmsFixture(HostLmsRepository hostLmsRepository,
		PatronIdentityFixture patronIdentityFixture) {

		this.hostLmsRepository = hostLmsRepository;
		this.patronIdentityFixture = patronIdentityFixture;
	}

	public void createHostLms(UUID id, String code) {
		Mono.from(hostLmsRepository.save(new DataHostLms(id, code,
				"Test Host LMS", "", Map.of())))
			.block();
	}

	public DataHostLms createHostLms_returnDataHostLms(UUID id, String code) {
		return Mono.from(hostLmsRepository.save(new DataHostLms(id, code,
				"Test Host LMS", "", Map.of())))
			.block();
	}

	public void deleteAllHostLMS() {
		patronIdentityFixture.deleteAllPatronIdentities();

		dataAccess.deleteAll(hostLmsRepository.findAll(),
			hostLms -> hostLmsRepository.delete(hostLms.getId()));
	}
}

package org.olf.reshare.dcb.test;

import java.util.Map;
import java.util.UUID;

import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.storage.HostLmsRepository;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class HostLmsFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final HostLmsRepository hostLmsRepository;

	public HostLmsFixture(HostLmsRepository hostLmsRepository) {
		this.hostLmsRepository = hostLmsRepository;
	}

	public void createHostLms(UUID id, String code) {
		Mono.from(hostLmsRepository.save(new DataHostLms(id, code,
				"Test Host LMS", "", Map.of())))
			.block();
	}

	public void deleteAllHostLMS() {
		dataAccess.deleteAll(hostLmsRepository.findAll(),
			hostLms -> hostLmsRepository.delete(hostLms.getId()));
	}
}

package org.olf.dcb.request.lifecycle.ncip;

import jakarta.inject.Singleton;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.HostLmsRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class NcipPeerHostLmsResolver {
	private final HostLmsRepository hostLmsRepository;

	public NcipPeerHostLmsResolver(HostLmsRepository hostLmsRepository) {
		this.hostLmsRepository = hostLmsRepository;
	}

	public Mono<DataHostLms> findBySystemId(String systemId) {
		return Flux.from(hostLmsRepository.queryAll())
			.filter(hostLms -> new NcipHostLmsConfiguration()
				.findNcipSystemId(hostLms.getClientConfig())
				.filter(systemId::equals)
				.isPresent())
			.singleOrEmpty()
			.switchIfEmpty(Mono.error(() -> new IllegalArgumentException(
				"No HostLMS is configured for NCIP SystemId " + systemId)));
	}
}

package org.olf.dcb.core.svc;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class AgencyService {
	private final AgencyRepository agencyRepository;

	public AgencyService(AgencyRepository agencyRepository) {
		this.agencyRepository = agencyRepository;
	}

	public Mono<DataAgency> findByCode(String code) {
		return Mono.from(agencyRepository.findOneByCode(code));
	}
}

package org.olf.dcb.core.svc;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.storage.AgencyRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class AgencyService {
	private final AgencyRepository agencyRepository;
	private final HostLmsService hostLmsService;

	public AgencyService(AgencyRepository agencyRepository, HostLmsService hostLmsService) {
		this.agencyRepository = agencyRepository;
		this.hostLmsService = hostLmsService;
	}

	public Mono<DataAgency> findByCode(String code) {
		return Mono.from(agencyRepository.findOneByCode(code))
			.doOnSuccess(agency -> log.debug("Found agency: {}", agency))
			.flatMap(this::enrichWithHostLms);
	}

	public Mono<DataAgency> findById(UUID id) {
		return Mono.from(agencyRepository.findById(id));
	}

	private Mono<DataAgency> enrichWithHostLms(DataAgency agency) {
		return Mono.just(agency)
			.zipWhen(this::findHostLms, DataAgency::setHostLms)
			.switchIfEmpty(Mono.just(agency));
	}

	private Mono<DataHostLms> findHostLms(DataAgency agency) {
		final var hostLmsId = getValue(getValue(agency, Agency::getHostLms), HostLms::getId);

		if (hostLmsId == null) {
			return Mono.empty();
		}

		return hostLmsService.findById(hostLmsId);
	}
}

package org.olf.dcb.core.svc;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.ReactorUtils.fetchRelatedRecord;

import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.storage.AgencyRepository;

import jakarta.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Value
@Singleton
public class AgencyService {
	AgencyRepository agencyRepository;
	HostLmsService hostLmsService;

	public Mono<DataAgency> findByCode(String code) {
		return Mono.from(agencyRepository.findOneByCode(code))
			.doOnSuccess(agency -> log.debug("Found agency: {}", agency))
			.flatMap(this::enrichWithHostLms);
	}

	public Mono<DataAgency> findById(UUID id) {
		log.debug("findById({})", id);

		return Mono.from(agencyRepository.findById(id));
	}

	private Mono<DataAgency> enrichWithHostLms(DataAgency agency) {
		return fetchRelatedRecord(agency, this::findHostLms, DataAgency::setHostLms);
	}

	private Mono<DataHostLms> findHostLms(DataAgency agency) {
		final var hostLmsId = getValueOrNull(agency, Agency::getHostLms, HostLms::getId);

		if (hostLmsId == null) {
			return Mono.empty();
		}

		return hostLmsService.findById(hostLmsId);
	}
}

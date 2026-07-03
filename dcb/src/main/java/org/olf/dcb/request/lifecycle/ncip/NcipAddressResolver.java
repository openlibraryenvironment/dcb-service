package org.olf.dcb.request.lifecycle.ncip;

import jakarta.inject.Singleton;
import java.util.List;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.storage.AgencyRepository;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Singleton
public class NcipAddressResolver {
	private final AgencyRepository agencyRepository;
	private final HostLmsService hostLmsService;
	private final NcipHostLmsConfiguration hostLmsConfiguration;

	NcipAddressResolver(
		AgencyRepository agencyRepository,
		HostLmsService hostLmsService) {

		this.agencyRepository = agencyRepository;
		this.hostLmsService = hostLmsService;
		this.hostLmsConfiguration = new NcipHostLmsConfiguration();
	}

	String agencyIdForHost(HostLms hostLms) {
		return hostLmsConfiguration.ncipAgencyIdFor(hostLms);
	}

	String systemIdForHost(HostLms hostLms) {
		return hostLmsConfiguration.ncipSystemIdFor(hostLms);
	}

	Mono<String> agencyIdForLocalAgencyCode(String agencyCode, String fallback) {
		if (!hasText(agencyCode)) {
			return Mono.just(requireText(fallback, "fallback"));
		}
		return findHostLmsForAgencyCode(agencyCode)
			.map(this::agencyIdForHost)
			.switchIfEmpty(Mono.just(agencyCode));
	}

	private Mono<DataHostLms> findHostLmsForAgencyCode(String agencyCode) {
		Publisher<java.util.UUID> hostLmsIdPublisher = agencyRepository.findHostLmsIdByAgencyCodes(List.of(agencyCode));
		if (hostLmsIdPublisher == null) {
			return Mono.empty();
		}
		return Mono.from(hostLmsIdPublisher)
			.flatMap(hostLmsService::findById);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String requireText(String value, String name) {
		if (!hasText(value)) {
			throw new IllegalArgumentException(name + " is required");
		}
		return value;
	}
}

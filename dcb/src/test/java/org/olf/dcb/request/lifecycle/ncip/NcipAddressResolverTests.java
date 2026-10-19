package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.AgencyRepository;
import reactor.core.publisher.Mono;

class NcipAddressResolverTests {
	private final AgencyRepository agencyRepository = mock(AgencyRepository.class);
	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final NcipAddressResolver resolver = new NcipAddressResolver(agencyRepository, hostLmsService);

	@Test
	void usesTheNcipAgencyIdOfAnNcipHost() {
		agencyOnHost("ors-unseen", Map.of(
			NcipHostLmsConfiguration.NCIP_SYSTEM_ID_KEY, "unseen-system",
			"ncip-agency-id", "UNSEEN"));

		assertThat(singleValueFrom(resolver.agencyIdForLocalAgencyCode("ors-unseen", "dcb-agency")),
			is("UNSEEN"));
	}

	@Test
	void usesTheAgencyCodeWhenItsHostHasNoNcipIdentity() {
		agencyOnHost("ung", Map.of("base-url", "https://alma.example.org"));

		assertThat(singleValueFrom(resolver.agencyIdForLocalAgencyCode("ung", "dcb-agency")),
			is("ung"));
	}

	private void agencyOnHost(String agencyCode, Map<String, Object> clientConfig) {
		final var hostLmsId = UUID.randomUUID();
		when(agencyRepository.findHostLmsIdByAgencyCodes(List.of(agencyCode)))
			.thenReturn(Mono.just(hostLmsId));
		when(hostLmsService.findById(hostLmsId))
			.thenReturn(Mono.just(DataHostLms.builder()
				.id(hostLmsId)
				.code(agencyCode.toUpperCase())
				.clientConfig(clientConfig)
				.build()));
	}
}

package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.HostLmsRepository;
import reactor.core.publisher.Flux;

class NcipPeerHostLmsResolverTests {
	@Test
	void resolvesConfiguredNcipSystemIdRatherThanHostCode() {
		final var repository = mock(HostLmsRepository.class);
		when(repository.queryAll()).thenReturn(Flux.just(
			DataHostLms.builder()
				.code("ors-unseen")
				.clientConfig(Map.of("ncip-system-id", "ors:unseen"))
				.build()));

		final var resolved = new NcipPeerHostLmsResolver(repository)
			.findBySystemId("ors:unseen").block();

		assertThat(resolved.getCode(), is("ors-unseen"));
	}

	@Test
	void rejectsUnknownNcipSystemId() {
		final var repository = mock(HostLmsRepository.class);
		when(repository.queryAll()).thenReturn(Flux.empty());

		assertThrows(IllegalArgumentException.class, () ->
			new NcipPeerHostLmsResolver(repository).findBySystemId("unknown").block());
	}
}

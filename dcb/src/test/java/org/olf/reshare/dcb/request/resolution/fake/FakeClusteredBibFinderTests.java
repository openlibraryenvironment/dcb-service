package org.olf.reshare.dcb.request.resolution.fake;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;

class FakeClusteredBibFinderTests {
	@Test
	void shouldAlwaysFindClusteredBib() {
		final var clusteredBibFinder = new FakeClusteredBibFinder();

		final var bibClusterId = UUID.randomUUID();

		final var foundClusteredBib = clusteredBibFinder
			.findClusteredBib(bibClusterId).block();

		assertThat(foundClusteredBib, is(notNullValue()));
		assertThat(foundClusteredBib.getId(), is(bibClusterId));

		final var fakebib = foundClusteredBib.getBibs().get(0);

		assertThat(fakebib.getId(), is(notNullValue()));
		assertThat(fakebib.getBibRecordId(), is("FAKE_BIB_RECORD_ID"));

		final var hostLms = fakebib.getHostLms();

		assertThat(hostLms, is(notNullValue()));
		assertThat(hostLms.getId(), is(notNullValue()));
		assertThat(hostLms.getCode(), is("FAKE_HOST_LMS_CODE"));
		assertThat(hostLms.getName(), is("Fake Host LMS"));
		assertThat(hostLms.getType(), is(SierraLmsClient.class));
		assertThat(hostLms.getClientConfig(), is(Map.of()));
	}
}

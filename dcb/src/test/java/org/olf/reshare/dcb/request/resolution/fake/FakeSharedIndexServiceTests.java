package org.olf.reshare.dcb.request.resolution.fake;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class FakeSharedIndexServiceTests {
	@Test
	void shouldAlwaysFindClusteredBib() {
		final var sharedIndex = new FakeSharedIndexService();

		final var bibClusterId = UUID.randomUUID();

		final var foundClusteredBib = sharedIndex
			.findClusteredBib(bibClusterId).block();

		assertThat(foundClusteredBib, is(notNullValue()));
		assertThat(foundClusteredBib.getId(), is(bibClusterId));

		final var fakebib = foundClusteredBib.getBibs().get(0);

		assertThat(fakebib.getId(), is(notNullValue()));
		assertThat(fakebib.getBibRecordId(), is("FAKE_BIB_RECORD_ID"));
		assertThat(fakebib.getHostLmsCode(), is("FAKE_HOST_LMS_CODE"));
	}
}

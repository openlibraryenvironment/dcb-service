package org.olf.reshare.dcb.request.resolution.fake;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class FakeSharedIndexServiceTests {
	@Test
	void shouldAlwaysFindClusteredBibWithSingleItem() {
		final var sharedIndex = new FakeSharedIndexService();

		final var bibClusterId = UUID.randomUUID();

		final var foundClusteredBib = sharedIndex
			.findClusteredBib(bibClusterId).block();

		assertThat(foundClusteredBib, is(notNullValue()));
		assertThat(foundClusteredBib.id(), is(bibClusterId));

		assertThat(foundClusteredBib.holdings(), is(notNullValue()));
		assertThat(foundClusteredBib.holdings(), hasSize(1));

		final var onlyHoldings = foundClusteredBib.holdings().get(0);

		assertThat(onlyHoldings, is(notNullValue()));
		assertThat(onlyHoldings.agency(), is(notNullValue()));
		assertThat(onlyHoldings.agency().code(), is("fake agency"));

		assertThat(onlyHoldings.items(), is(notNullValue()));
		assertThat(onlyHoldings.items(), hasSize(1));

		final var onlyItem = onlyHoldings.items().get(0);

		assertThat(onlyItem, is(notNullValue()));
		assertThat(onlyItem.id(), is(notNullValue()));
	}
}

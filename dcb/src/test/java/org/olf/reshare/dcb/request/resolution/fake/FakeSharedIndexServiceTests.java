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
		assertThat(foundClusteredBib.getId(), is(bibClusterId));

		assertThat(foundClusteredBib.getHoldings(), is(notNullValue()));
		assertThat(foundClusteredBib.getHoldings(), hasSize(1));

		final var onlyHoldings = foundClusteredBib.getHoldings().get(0);

		assertThat(onlyHoldings, is(notNullValue()));

		final var agency = onlyHoldings.getAgency();

		assertThat(agency, is(notNullValue()));
		assertThat(agency.getCode(), is("fake agency"));

		final var items = onlyHoldings.getItems();

		assertThat(items, is(notNullValue()));
		assertThat(items, hasSize(1));

		final var onlyItem = items.get(0);

		assertThat(onlyItem, is(notNullValue()));
		assertThat(onlyItem.getId(), is(notNullValue()));
	}
}

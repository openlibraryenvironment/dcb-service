package org.olf.dcb.utils;

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.utils.CollectionUtils.concatenate;

import org.junit.jupiter.api.Test;

class CollectionUtilsTests {
	@Test
	void shouldTolerateNullFirstCollectionWhenConcatenating() {
		assertThat(concatenate(null, emptyList()), is(empty()));
	}

	@Test
	void shouldTolerateNullSecondCollectionWhenConcatenating() {
		assertThat(concatenate(emptyList(), null), is(empty()));
	}
}

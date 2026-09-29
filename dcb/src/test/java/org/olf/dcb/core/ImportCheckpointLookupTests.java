package org.olf.dcb.core;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
class ImportCheckpointLookupTests {
	@Inject
	private HostLmsService hostLmsService;

	@Test
	void shouldReadACheckpointOutsideAnyCallersTransaction() {
		// The checkpoint repository is Propagation.MANDATORY, so this throws unless the
		// lookup opens its own transaction.
		final var checkpoint = singleValueFrom(hostLmsService.findImportCheckpoint(randomUUID()));

		assertThat(checkpoint.isObject(), is(true));
		assertThat(checkpoint.size(), is(0));
	}
}

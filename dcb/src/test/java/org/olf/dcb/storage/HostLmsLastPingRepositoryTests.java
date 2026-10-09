package org.olf.dcb.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DcbTest
class HostLmsLastPingRepositoryTests {
	private static final String CODE = "LAST-PING-WRITES";

	@Inject
	HostLmsFixture hostLmsFixture;
	@Inject
	HostLmsRepository hostLmsRepository;

	// Without @SingleResult about one write in five was not read back, so 100 cannot pass by chance
	@Test
	void everyLastPingWrittenCanBeReadBack() {
		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms(CODE);

		final var lost = new ArrayList<Integer>();

		for (int i = 0; i < 100; i++) {
			final var lastPing = "{\"n\": " + i + "}";

			Mono.from(hostLmsRepository.updateLastPing(CODE, lastPing)).block();

			// Read in full: reading with Mono.from as well hid the lost writes, and this passed without the fix
			final var read = Flux.from(hostLmsRepository.findLastPingByCode(CODE)).blockLast();

			if (read == null || !read.equals(lastPing)) {
				lost.add(i);
			}
		}

		assertThat("writes not read back", lost, empty());
	}
}

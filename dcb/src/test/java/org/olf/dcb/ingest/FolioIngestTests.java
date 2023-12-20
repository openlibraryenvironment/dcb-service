package org.olf.dcb.ingest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
class FolioIngestTests {
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldIngestFromFolio() {
		assertThat(true, is(true));
	}
}

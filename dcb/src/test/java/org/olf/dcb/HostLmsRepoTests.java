package org.olf.dcb;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;


@DcbTest
class HostLmsRepoTests {
	@Inject
	HostLmsRepository hostLmsRepository;

	@Inject
	HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		hostLmsFixture.deleteAll();
	}

	@Test
	void createHostLmsViaRepository() {
		final var new_host_lms = DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("TCODE1")
			.name("Test HostLMS1")
			.lmsClientClass("org.olf.dcb.core.interaction.sierra.SierraLmsClient")
			.clientConfig(Map.of())
			.build();

		singleValueFrom(hostLmsRepository.save(new_host_lms));

		Boolean exists = Mono.from(hostLmsRepository.existsById(new_host_lms.getId())).block();

		assertThat(exists, is(true));
	}
}

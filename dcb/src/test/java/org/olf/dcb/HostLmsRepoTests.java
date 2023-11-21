package org.olf.dcb;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.test.DcbTest;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;


@DcbTest
class HostLmsRepoTests {
	@Inject
	@Client("/")
	HttpClient client;

	@Inject
	HostLmsRepository hostLmsRepository;

	@BeforeEach
	void beforeEach() {
	}

	@Test
	void createHostLmsViaRepository() {
		Map<String, Object> cfg = new HashMap<String, Object>();
		DataHostLms new_host_lms = new DataHostLms(UUID.randomUUID(),
			"TCODE1",
			"Test HostLMS1",
			"org.olf.dcb.core.interaction.sierra.SierraLmsClient",
			cfg);

		Mono.from(hostLmsRepository.save(new_host_lms))
			.block();

		Boolean exists = Mono.from(hostLmsRepository.existsById(new_host_lms.getId())).block();

		assertThat(exists, is(Boolean.TRUE));
	}
}

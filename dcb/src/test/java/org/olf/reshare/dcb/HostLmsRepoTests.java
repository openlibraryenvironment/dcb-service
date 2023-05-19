package org.olf.reshare.dcb;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import org.olf.reshare.dcb.test.DcbTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;


@DcbTest
class HostLmsRepoTests {

        private final Logger log = LoggerFactory.getLogger(HostLmsRepoTests.class);


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
		Map<String, Object> cfg = new HashMap<String,Object>();
        	DataHostLms new_host_lms = new DataHostLms(UUID.randomUUID(),
                                                           "TCODE1",
                                                           "Test HostLMS1",
                                                           "org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient",
                                                           cfg);

        	Mono.from(hostLmsRepository.save(new_host_lms))
                  .block();

        	// final var fetchedHostLMSRecords = Flux.from(hostLmsRepository.findAll())
                //                 .collectList()
                //                 .block();
		// log.info("fetched host records: {}",fetchedHostLMSRecords);

                Boolean exists = Mono.from(hostLmsRepository.existsById(new_host_lms.getId())).block();

        	assertThat(exists, is(Boolean.TRUE));
	}
}

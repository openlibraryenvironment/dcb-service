package org.olf.reshare.dcb;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.OK;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import org.olf.reshare.dcb.test.DcbTest;
import org.olf.reshare.dcb.core.model.DataHostLms;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import net.minidev.json.JSONObject;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

package org.olf.reshare.dcb.storage;

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
import org.olf.reshare.dcb.storage.AgencyRepository;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import org.olf.reshare.dcb.test.DcbTest;
import org.olf.reshare.dcb.core.model.DataAgency;
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

@DcbTest
class AgencyRepoTests {

	@Inject
	@Client("/")
	HttpClient client;

	@Inject
	AgencyRepository agencyRepository;

	@Inject
	HostLmsRepository hostLmsRepository;

	@BeforeEach
	void beforeEach() {
	}

	@Test
	void createAgencyViaRepository() {

		// Create a host LMS entry for our new Agency to point at
                Map<String, Object> cfg = new HashMap<String,Object>();
                DataHostLms new_host_lms = new DataHostLms(UUID.randomUUID(),
                                                           "Test HostLMS2",
                                                           "org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient",
                                                           cfg);

                Mono.from(hostLmsRepository.save(new_host_lms)).block();

        	DataAgency new_agency = new DataAgency(UUID.randomUUID(), "Test Agency Name1", new_host_lms);

        	Mono.from(agencyRepository.save(new_agency)).block();

        	final var fetchedAgencyRecords = Flux.from(agencyRepository.findAll())
                                .collectList()
                                .block();

        	assertThat(fetchedAgencyRecords.size(), is(1));
	}
}

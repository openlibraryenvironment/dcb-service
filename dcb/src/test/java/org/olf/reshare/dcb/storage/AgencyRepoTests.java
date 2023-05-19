package org.olf.reshare.dcb.storage;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.DataAgency;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.test.DcbTest;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
                                                           "TH2",  // Code
                                                           "Test HostLMS2",  // Name
                                                           "org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient",
                                                           cfg);

                Mono.from(hostLmsRepository.save(new_host_lms)).block();

        	DataAgency new_agency = new DataAgency(UUID.randomUUID(), "ACODE1", "Test Agency Name1", new_host_lms);

        	Mono.from(agencyRepository.save(new_agency)).block();

        	final var fetchedAgencyRecords = Flux.from(agencyRepository.findAll())
                                .collectList()
                                .block();

        	assertThat(fetchedAgencyRecords.size(), is(1));
	}
}

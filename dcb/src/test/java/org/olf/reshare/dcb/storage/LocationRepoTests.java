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
import org.olf.reshare.dcb.storage.LocationRepository;
import org.olf.reshare.dcb.test.DcbTest;
import org.olf.reshare.dcb.core.model.Location;

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
class LocationRepoTests {

	@Inject
	@Client("/")
	HttpClient client;

	@Inject
	LocationRepository locationRepository;

	@BeforeEach
	void beforeEach() {
	}

	@Test
	void createLocationViaRepository() {

		// Create a host LMS entry for our new Agency to point at
                Location new_location = new Location()
                                              .builder()
                                                .id(UUID.randomUUID())
                                                .code("Location1")
                                                .name("Location1 Name")
                                                .build();

                Mono.from(locationRepository.save(new_location)).block();

        	final var fetchedLocationRecords = Flux.from(locationRepository.findAll())
                                .collectList()
                                .block();

        	assertThat(fetchedLocationRecords.size(), is(1));
	}
}

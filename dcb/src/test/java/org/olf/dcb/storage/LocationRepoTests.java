package org.olf.dcb.storage;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.LocationSymbol;
import org.olf.dcb.storage.LocationRepository;
import org.olf.dcb.storage.LocationSymbolRepository;
import org.olf.dcb.test.DcbTest;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DcbTest
class LocationRepoTests {

	@Inject
	@Client("/")
	HttpClient client;

	@Inject
	LocationRepository locationRepository;

	@Inject
	LocationSymbolRepository locationSymbolRepository;

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
                                                .type("Location")  // Campus, Library, Location, Pickup Location
                                                .isPickup(Boolean.TRUE)  // Campus, Library, Location, Pickup Location
                                                .latitude(53.383331)
                                                .longitude(-1.466667)
                                                .build();

                Mono.from(locationRepository.save(new_location)).block();

                // Create a location symbol that can be used to identify this location
                LocationSymbol new_ls1 = new LocationSymbol()
                                              .builder()
                                                .id(UUID.randomUUID())
                                                .authority("DCBTEST")
                                                .code("LOC1")
                                                .location(new_location)
                                                .build();
                Mono.from(locationSymbolRepository.save(new_ls1)).block();

                LocationSymbol new_ls2 = new LocationSymbol()
                                              .builder()
                                                .id(UUID.randomUUID())
                                                .authority("CARDINAL")
                                                .code("SSW")
                                                .location(new_location)
                                                .build();
                Mono.from(locationSymbolRepository.save(new_ls2)).block();

        	final var fetchedLocationRecords = Flux.from(locationRepository.queryAll())
                                .collectList()
                                .block();

        	assertThat(fetchedLocationRecords.size(), is(1));

        	final var fetchedLocationSymbolRecords = Flux.from(locationSymbolRepository.queryAll())
                                .collectList()
                                .block();

        	assertThat(fetchedLocationSymbolRecords.size(), is(2));
	}
}

package org.olf.reshare.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static io.micronaut.http.HttpStatus.OK;
import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.storage.BibRepository;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;
import org.olf.reshare.dcb.test.DcbTest;
import org.olf.reshare.dcb.test.PatronRequestsDataAccess;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import net.minidev.json.JSONObject;
import reactor.core.publisher.Mono;

@DcbTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClusterRecordTests {

        @Inject
        @Client("/")
        private HttpClient client;

        @Test
        void getClusterRecords() {
                // These are separate variables to only have single invocation in assertThrows
                final var blockingClient = client.toBlocking();
                final var request = HttpRequest.GET("/clusters");
                final var response =blockingClient.exchange(request);
		assertThat(response.getStatus(), is(OK));
        }

}

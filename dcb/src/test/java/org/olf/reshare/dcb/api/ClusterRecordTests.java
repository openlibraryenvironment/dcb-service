package org.olf.reshare.dcb.api;

import static io.micronaut.http.HttpStatus.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.reshare.dcb.test.DcbTest;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;

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

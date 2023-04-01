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
import org.olf.reshare.dcb.core.ProcessStateService;
import org.olf.reshare.dcb.test.DcbTest;

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
class ProcessStateServiceTests {

	@Inject
	ProcessStateService processStateService;

	@BeforeEach
	void beforeEach() {
	}

	@Test
	void processStateLifecycle() {
		Map<String, Object> test_state = new HashMap<String,Object>();
                test_state.put("key1","value1");

                UUID test_context = UUID.randomUUID();
                processStateService.updateState(test_context, "testProcess", test_state).block();

                Map<String, Object> current_state = processStateService.getState(test_context, "testProcess");

                assert(current_state.get("key1").equals("value1"));
	}
}

package org.olf.reshare.dcb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.EmbeddedApplication;
import jakarta.inject.Inject;

@DcbTest
class DcbAppTest {

	@Inject
	EmbeddedApplication<?> application;

	@Inject
	@Client("/")
	HttpClient client;

	@Test
	void testItWorks () {
		Assertions.assertTrue(application.isRunning());
	}

	// @Test
	// void testDcbRootPath () {
	// final String text = client.toBlocking().retrieve(HttpRequest.GET("/"),
	// String.class);
	// assertEquals("Example Response", text);
	// }

}
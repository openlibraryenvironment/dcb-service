package org.olf.reshare.dcb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@MicronautTest(transactional = false)
class DcbTest {

  @Inject
  EmbeddedApplication<?> application;

  @Inject
  @Client("/")
  HttpClient client;

  @Test
  void testItWorks () {
    Assertions.assertTrue(application.isRunning());
  }

//  @Test
//  void testDcbRootPath () {
//    final String text = client.toBlocking().retrieve(HttpRequest.GET("/"),
//        String.class);
//    assertEquals("Example Response", text);
//  }

}
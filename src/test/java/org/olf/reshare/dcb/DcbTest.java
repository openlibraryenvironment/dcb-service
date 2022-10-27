package org.olf.reshare.dcb;

import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

  //Correct UN/PW provides a token
  @Test
  void testDcbCredetialsToken (RequestSpecification spec) {
	  spec
	  	.given().auth().preemptive().basic("user", "password")
	  	.when().get("/dcb").header("Authorization");	  
  }
  
  // No user name and password provides 400 Bad Request
  @Test
  void testDcbNoCredetials (RequestSpecification spec) {
	  spec
		  .when().get("/dcb")
		  .then().assertThat().statusCode(400);
  }
  
  // Correct UN/PW provides 200 OK
  @Test
  void testDcbCorrectCredetials (RequestSpecification spec) {
	  spec
		  .given().auth().preemptive().basic("user", "password")
		  .when().get("/dcb")
		  .then().assertThat().statusCode(200);
  }
  
  // Incorrect UN/PW provides 401 Unauthorized
  @Test
  void testDcbIncorrectCredetials (RequestSpecification spec) {
	  spec
		  .given().auth().preemptive().basic("wrong", "wrong")
		  .when().get("/dcb")
		  .then().assertThat().statusCode(401);
  }
}

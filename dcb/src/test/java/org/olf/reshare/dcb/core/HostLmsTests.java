package org.olf.reshare.dcb.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Parameter.param;

import java.io.IOException;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpTemplate.TemplateType;
import org.mockserver.model.MediaType;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.validation.constraints.NotEmpty;

import org.junit.jupiter.api.BeforeAll;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.model.HostLms;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MockServerMicronautTest
@MicronautTest(transactional = false, propertySources = { "classpath:tests/hostLmsProps.yml" })
@TestMethodOrder(OrderAnnotation.class)
public class HostLmsTests {
	
	@BeforeAll
	public static void addFakeSierraApis(MockServerClient mock) {
		
		// Mock login to sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withHeader("host", "test1.com")
				.withHeader("Authorization", "Basic dGVzdDEta2V5OnRlc3QxLXNlY3JldA==")
				.withMethod("POST")
				.withPath("/iii/sierra-api/v6/token"))
		.respond(
				response()
					.withStatusCode(200)
					.withContentType(MediaType.APPLICATION_JSON)
					.withBody(
						json("{"
								+ "		\"access_token\": \"test1_auth_token\"," 
								+ "		\"token_type\": \"Bearer\","
								+ "		\"expires_in\": 3600"
								+ "}"
						)));
		
		mock.when(
				request()
					.withHeader("Accept", "application/json")
					.withHeader("host", "test2.com")
					.withHeader("Authorization", "Basic dGVzdDIta2V5Mjp0ZXN0Mi1zZWNyZXQ=")
					.withMethod("POST")
					.withPath("/iii/sierra-api/v6/token"))
			.respond(
					response()
						.withStatusCode(200)
						.withContentType(MediaType.APPLICATION_JSON)
						.withBody(
							json("{"
									+ "		\"access_token\": \"test2_auth_token\"," 
									+ "		\"token_type\": \"Bearer\","
									+ "		\"expires_in\": 3600"
									+ "}"
							)));
		
		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withHeader("host", "test1.com")
				.withHeader("Authorization", "Bearer test1_auth_token")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/*"))
		
			.respond(
				response()
					.withStatusCode(200)
					.withContentType(MediaType.APPLICATION_JSON)
					.withBody(
						json("{"
								+ "	\"total\": 2,"
								+ "	\"start\": 0,"
								+ "	\"entries\": ["
								+ "		{"
								+ "			\"id\": \"id1\","
								+ "			\"deleted\": false,"
								+ "			\"title\": \"Test1 record 1\","
								+ "			\"author\": \"Wojciechowska, Maia,\","
								+ "			\"publishYear\": 1969"
								+ "		},"
								+ "		{"
								+ "			\"id\": \"id2\","
								+ "			\"deleted\": false,"
								+ "			\"title\": \"Test1 record 2\","
								+ "			\"author\": \"Smith, John,\","
								+ "			\"publishYear\": 2010"
								+ "		}"
								+ "	]"
								+ "}")));
	
		// 2nd target
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withHeader("host", "test2.com")
				.withHeader("Authorization", "Bearer test2_auth_token")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/*"))
		
			.respond(
				response()
					.withStatusCode(200)
					.withContentType(MediaType.APPLICATION_JSON)
					.withBody(
						json("{\n"
								+ "	\"total\": 1,\n"
								+ "	\"start\": 0,\n"
								+ "	\"entries\": [\n"
								+ "		{\n"
								+ "			\"id\": \"1\",\n"
								+ "			\"deleted\": false,\n"
								+ "			\"title\": \"Test2 record\",\n"
								+ "			\"author\": \"Wojciechowska, Maia,\",\n"
								+ "			\"publishYear\": 1969\n"
								+ "		}\n"
								+ "	]\n"
								+ "}")));
		
		
		// Catch all others. 403.
		mock.when(
	      request(),
	      Times.unlimited(),
	      TimeToLive.unlimited(),
	      -10
	  )
	  .respond(
	      response()
	          .withStatusCode(403)
	  );
	}

	@Inject
	ResourceLoader loader;
	
	@Inject
	HostLmsService manager;

	@Test
	public void hostLmsFromConfigLoaded() {
		
		// Get the list of configured host LMS
		List<HostLms> allLms =  manager.getAllHostLms()
			.sort((lms1, lms2) -> lms1.getName().compareTo(lms2.getName()))
			.collect(Collectors.toUnmodifiableList())			
			.block();
		
		assertEquals(2, allLms.size());
		
		final HostLms test1 = allLms.get(0);
		assertEquals(2, test1.getAgencies().size());
		
		final HostLms test2 = allLms.get(1);
		assertEquals(2, test2.getAgencies().size());
		
		List<Map<String,?>> results = manager.getClientFor(test1)
			.flatMapMany( client -> client.getAllBibData() )
			.collect(Collectors.toList()).block();
		
		assertEquals(2, results.size());
		
		results = manager.getClientFor(test2)
				.flatMapMany( client -> client.getAllBibData() )
				.collect(Collectors.toList()).block();
			
		assertEquals(1, results.size());
	}
	
	// Add an LMS to the Database

	// Check 3 LMSs

	// ReCheck resolution

	// Check validation of LMS across the

}

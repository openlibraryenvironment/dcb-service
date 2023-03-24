package org.olf.reshare.dcb.gokb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.gokb.GokbApiClient;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
public class GokbImportTests {

	@Inject
	GokbApiClient client;

	@Inject
	ResourceLoader loader;

	@Test
	public void testIntercepted(MockServerClient mock) throws IOException {

		// Mock the response from GOKb
		mock.when(
				request()
					.withHeader("host", "gokb.org")
					.withMethod("GET")
					.withPath("/gokb/api/scroll")
					.withQueryStringParameter(GokbApiClient.QUERY_PARAM_COMPONENT_TYPE, GokbApiClient.COMPONENT_TYPE_TIPP))
			.respond(
				response()
					.withStatusCode(200)
					.withContentType(MediaType.APPLICATION_JSON)
					.withBody(
						json(new String(loader.getResourceAsStream("classpath:mock-responses/gokb-tipp-scroll.json").orElseThrow().readAllBytes())))); // Empty list

		// Fetch from gokb and block
		var response = Mono.from(client.scrollTipps(null, null)).block();

		assertFalse(response.hasMoreRecords());
		assertEquals(response.records().size(), 3);
		assertEquals(response.records().size(), response.total());

//    	
//    
//    // Use the regular registry client to fetch the agreement mock.
//    List<?> resp = client.toBlocking().retrieve(HttpRequest.GET("/_/proxy/modules?filter=mod-agreements"),
//    		Argument.of(List.class, Argument.of(Map.class, String.class, Object.class)));
//
//  	assertNotNull(resp);
//  	assertEquals(resp.size(), 0);
	}
}

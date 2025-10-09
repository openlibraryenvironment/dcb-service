package services.k_int.test.mockserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;

@MockServerMicronautTest
@Requires(env = {"framework_test"})
@Disabled
public class ProxyTest {

	@Inject
	@Client("http://folio-registry.aws.indexdata.com")
	HttpClient client;

	@Test
	public void testProxied() {

		List<?> resp = client.toBlocking().retrieve(HttpRequest.GET("/_/proxy/modules?filter=mod-agreements"),
			Argument.of(List.class, Argument.of(Map.class, String.class, Object.class)));

		assertNotNull(resp);
		assertTrue(resp.size() > 0);
	}

	@Test
	public void testIntercepted(MockServerClient mock) {

		// Mock the response from the registry.
		mock.when(
				request()
					.withHeader("host", "folio-registry.aws.indexdata.com")
					.withMethod("GET")
					.withPath("/_/proxy/modules"))
			.respond(
				response()
					.withStatusCode(200)
					.withContentType(MediaType.APPLICATION_JSON)
					.withBody("[]")); // Empty list

		// Use the regular registry client to fetch the agreement mock.
		List<?> resp = client.toBlocking().retrieve(HttpRequest.GET("/_/proxy/modules?filter=mod-agreements"),
			Argument.of(List.class, Argument.of(Map.class, String.class, Object.class)));

		assertNotNull(resp);
		assertEquals(resp.size(), 0);
	}
}
